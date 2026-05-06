package com.sumicare.user.service;

import com.sumicare.user.domain.Role;
import com.sumicare.user.domain.User;
import com.sumicare.user.dto.CreateUserRequest;
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

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    public List<UserResponse> listForOrganization(UUID organizationId) {
        return userRepository.findAllByOrganizationId(organizationId).stream()
                .map(this::toResponse).toList();
    }

    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','MANAGER')")
    @Transactional
    public UserResponse createUser(UUID organizationId, CreateUserRequest request) {
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
        userRepository.save(user);
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
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user.setUpdatedAt(OffsetDateTime.now());
        return toResponse(user);
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(
                u.getId(), u.getOrganizationId(), u.getUsername(), u.getEmail(),
                u.getRole() == null ? null : u.getRole().getCode(),
                u.getDisplayName(), u.isActive(), u.getCreatedAt()
        );
    }
}
