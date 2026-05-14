package com.sumicare.user.service;

import com.sumicare.auth.domain.EmailVerificationToken;
import com.sumicare.auth.domain.PasswordResetToken;
import com.sumicare.auth.repository.EmailVerificationTokenRepository;
import com.sumicare.auth.repository.PasswordResetTokenRepository;
import com.sumicare.auth.service.EmailService;
import com.sumicare.user.domain.Role;
import com.sumicare.user.domain.User;
import com.sumicare.user.dto.CreateUserRequest;
import com.sumicare.user.dto.UpdateProfileRequest;
import com.sumicare.user.dto.UpdateUserRequest;
import com.sumicare.user.dto.UserResponse;
import com.sumicare.user.repository.RoleRepository;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailService emailService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, EmailVerificationTokenRepository tokenRepository,
                       PasswordResetTokenRepository resetTokenRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRepository = tokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.emailService = emailService;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<UserResponse> listForOrganization(UUID organizationId) {
        return userRepository.findAllByOrganizationId(organizationId).stream()
                .map(this::toResponse).toList();
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public UserResponse createUser(UUID organizationId, CreateUserRequest request) {
        validatePasswordStrength(request.password());
        Role role = roleRepository.findByCode(request.role())
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + request.role()));
        User user = new User();
        user.setOrganizationId(organizationId);
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setDisplayName(request.displayName());
        user.setActive(true);
        user.setEmailVerified(true);
        userRepository.save(user);

        if (request.email() != null && !request.email().isBlank()) {
            EmailVerificationToken evToken = new EmailVerificationToken();
            evToken.setUserId(user.getId());
            evToken.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
            evToken.setExpiresAt(OffsetDateTime.now().plusHours(24));
            tokenRepository.save(evToken);
            emailService.sendVerificationEmail(request.email(), request.displayName(), evToken.getToken());
        }

        return toResponse(user);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        if (request.displayName() != null) user.setDisplayName(request.displayName());
        if (request.email() != null) user.setEmail(request.email());
        if (request.role() != null) {
            Role role = roleRepository.findByCode(request.role()).orElseThrow();
            user.setRole(role);
        }
        if (request.active() != null) user.setActive(request.active());
        if (request.password() != null && !request.password().isBlank()) {
            validatePasswordStrength(request.password());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user.setUpdatedAt(OffsetDateTime.now());
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName());
        }
        user.setUpdatedAt(OffsetDateTime.now());
        return toResponse(user);
    }

    @Transactional
    public void requestPasswordReset(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Account has no email address configured for password reset");
        }
        String rawToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(userId);
        resetToken.setToken(rawToken);
        resetToken.setExpiresAt(OffsetDateTime.now().plusMinutes(30));
        resetTokenRepository.save(resetToken);
        String name = user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName() : user.getUsername();
        emailService.sendPasswordResetEmail(user.getEmail(), name, rawToken);
    }

    @Transactional
    public void consumePasswordReset(String token, String newPassword) {
        validatePasswordStrength(newPassword);
        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));
        if (resetToken.isConsumed()) {
            throw new IllegalArgumentException("Reset link has already been used");
        }
        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("Reset link has expired");
        }
        User user = userRepository.findById(resetToken.getUserId()).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(OffsetDateTime.now());
        resetToken.setConsumedAt(OffsetDateTime.now());
        resetTokenRepository.save(resetToken);
    }

    public UserResponse getById(UUID userId) {
        return toResponse(userRepository.findById(userId).orElseThrow());
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("Password must contain at least one digit");
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalArgumentException("Password must contain at least one special character");
        }
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(
                u.getId(), u.getOrganizationId(), u.getUsername(), u.getEmail(),
                u.getRole() == null ? null : u.getRole().getCode(),
                u.getDisplayName(), u.isActive(), u.getCreatedAt()
        );
    }
}
