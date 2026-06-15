package com.sumicare.user.service;

import com.sumicare.auth.domain.EmailVerificationToken;
import com.sumicare.auth.domain.PasswordResetToken;
import com.sumicare.auth.repository.EmailVerificationTokenRepository;
import com.sumicare.auth.repository.PasswordResetTokenRepository;
import com.sumicare.auth.service.EmailService;
import com.sumicare.auth.service.JwtService;
import com.sumicare.user.domain.Role;
import com.sumicare.user.domain.User;
import com.sumicare.user.dto.CreateUserRequest;
import com.sumicare.user.dto.UpdateProfileRequest;
import com.sumicare.user.dto.UpdateUserRequest;
import com.sumicare.user.dto.UserResponse;
import com.sumicare.user.repository.RoleRepository;
import com.sumicare.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

    private static final Set<String> ADMIN_EDITABLE_ROLES = Set.of("MANAGER", "RECEPTIONIST");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailService emailService;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, EmailVerificationTokenRepository tokenRepository,
                       PasswordResetTokenRepository resetTokenRepository,
                       EmailService emailService, JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenRepository = tokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.emailService = emailService;
        this.jwtService = jwtService;
    }

    private void enforceTierForTarget(String actorRole, User target) {
        String targetRole = target.getRole() == null ? null : target.getRole().getCode();
        if ("ADMIN".equals(actorRole)) {
            if (targetRole == null || !ADMIN_EDITABLE_ROLES.contains(targetRole)) {
                throw new AccessDeniedException("ADMIN can only manage MANAGER and RECEPTIONIST users");
            }
        } else if ("SUPERADMIN".equals(actorRole)) {
            if ("SUPERADMIN".equals(targetRole)) {
                throw new AccessDeniedException("Cannot modify another SUPERADMIN account");
            }
        }
    }

    private void enforceTierForNewRole(String actorRole, String newRole) {
        if ("ADMIN".equals(actorRole) && !ADMIN_EDITABLE_ROLES.contains(newRole)) {
            throw new AccessDeniedException("ADMIN cannot create or assign role: " + newRole);
        }
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<UserResponse> listForOrganization(UUID organizationId) {
        return userRepository.findAllByOrganizationIdAndActiveTrue(organizationId).stream()
                .map(this::toResponse).toList();
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<UserResponse> listDeactivatedForOrganization(UUID organizationId) {
        return userRepository.findAllByOrganizationIdAndActiveFalse(organizationId).stream()
                .map(this::toResponse).toList();
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @Transactional
    public UserResponse createUser(UUID organizationId, String actorRole, CreateUserRequest request) {
        enforceTierForNewRole(actorRole, request.role());
        if (request.email() != null && !request.email().isBlank()
                && userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalArgumentException("That email is already registered. Use a different email.");
        }
        Role role = roleRepository.findByCode(request.role())
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + request.role()));
        User user = new User();
        user.setOrganizationId(organizationId);
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setRole(role);
        user.setDisplayName(request.displayName());
        user.setActive(true);

        boolean inviteFlow = request.password() == null || request.password().isBlank();
        if (inviteFlow) {
            user.setPasswordHash(null);
            user.setEmailVerified(false);
        } else {
            validatePasswordStrength(request.password());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setEmailVerified(true);
        }
        userRepository.save(user);

        if (inviteFlow && request.email() != null && !request.email().isBlank()) {
            EmailVerificationToken invToken = new EmailVerificationToken();
            invToken.setUserId(user.getId());
            invToken.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
            invToken.setExpiresAt(OffsetDateTime.now().plusMinutes(30));
            invToken.setTokenType("INVITATION");
            tokenRepository.save(invToken);
            String displayName = request.displayName() != null ? request.displayName() : request.username();
            emailService.sendInvitationEmail(request.email(), displayName, invToken.getToken());
        } else if (!inviteFlow && request.email() != null && !request.email().isBlank()) {
            EmailVerificationToken evToken = new EmailVerificationToken();
            evToken.setUserId(user.getId());
            evToken.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
            evToken.setExpiresAt(OffsetDateTime.now().plusHours(24));
            tokenRepository.save(evToken);
            emailService.sendVerificationEmail(request.email(), request.displayName(), evToken.getToken());
        }

        return toResponse(user);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @Transactional
    public UserResponse updateUser(UUID actorId, String actorRole, UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        requireSameOrganization(actorId, user);
        if (!actorId.equals(userId)) {
            enforceTierForTarget(actorRole, user);
        }
        if (request.displayName() != null) user.setDisplayName(request.displayName());
        if (request.email() != null) user.setEmail(request.email());
        if (request.role() != null) {
            enforceTierForNewRole(actorRole, request.role());
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

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @Transactional
    public void deactivateUser(UUID actorId, String actorRole, UUID userId) {
        if (actorId.equals(userId)) {
            throw new IllegalArgumentException("Cannot deactivate your own account");
        }
        User target = userRepository.findById(userId).orElseThrow();
        requireSameOrganization(actorId, target);
        enforceTierForTarget(actorRole, target);
        target.setActive(false);
        target.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(target);
        jwtService.revokeAllForUser(userId);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @Transactional
    public UserResponse reactivateUser(UUID actorId, String actorRole, UUID userId) {
        User target = userRepository.findById(userId).orElseThrow();
        requireSameOrganization(actorId, target);
        enforceTierForTarget(actorRole, target);
        target.setActive(true);
        target.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(target);
        return toResponse(target);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @Transactional
    public UserResponse unlockUser(UUID actorId, String actorRole, UUID userId) {
        User target = userRepository.findById(userId).orElseThrow();
        requireSameOrganization(actorId, target);
        enforceTierForTarget(actorRole, target);
        target.setAccountLocked(false);
        target.setFailedLoginAttempts(0);
        target.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(target);
        return toResponse(target);
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
        resetToken.setExpiresAt(OffsetDateTime.now().plusHours(1));
        resetTokenRepository.save(resetToken);
        String name = user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName() : user.getUsername();
        emailService.sendPasswordResetEmail(user.getEmail(), name, rawToken);
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    @Transactional
    public void sendResetLink(UUID actorId, String actorRole, UUID targetUserId) {
        User target = userRepository.findById(targetUserId).orElseThrow();
        requireSameOrganization(actorId, target);
        enforceTierForTarget(actorRole, target);
        requestPasswordReset(targetUserId);
    }

    private void requireSameOrganization(UUID actorId, User target) {
        User actor = userRepository.findById(actorId).orElseThrow();
        if (!actor.getOrganizationId().equals(target.getOrganizationId())) {
            throw new AccessDeniedException("User belongs to another organization.");
        }
    }

    public List<User> listOrgAdmins(UUID organizationId) {
        return userRepository.findAllByOrganizationIdAndActiveTrue(organizationId).stream()
                .filter(u -> {
                    String code = u.getRole() == null ? null : u.getRole().getCode();
                    return "ADMIN".equals(code) || "SUPERADMIN".equals(code);
                })
                .toList();
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

    @Transactional
    public void redeemInvitation(String token, String newPassword) {
        validatePasswordStrength(newPassword);
        EmailVerificationToken invToken = tokenRepository.findByTokenAndTokenType(token, "INVITATION")
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation link"));
        if (invToken.isConsumed()) {
            throw new IllegalArgumentException("Invitation has already been used");
        }
        if (invToken.isExpired()) {
            throw new IllegalArgumentException("Invitation link has expired");
        }
        User user = userRepository.findById(invToken.getUserId()).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        user.setUpdatedAt(OffsetDateTime.now());
        invToken.setConsumedAt(OffsetDateTime.now());
        tokenRepository.save(invToken);
        userRepository.save(user);
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
                u.getDisplayName(), u.isActive(), u.isAccountLocked(), u.getCreatedAt()
        );
    }
}
