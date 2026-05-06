package com.sumicare.user.bootstrap;

import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.user.domain.Role;
import com.sumicare.user.domain.User;
import com.sumicare.user.repository.RoleRepository;
import com.sumicare.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SuperadminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${sumicare.bootstrap.defaultSuperadminUsername:superadmin}")
    private String defaultUsername;

    @Value("${sumicare.bootstrap.defaultSuperadminPassword:ChangeMe!12345}")
    private String defaultPassword;

    @Value("${sumicare.bootstrap.defaultOrganizationSlug:lasema}")
    private String defaultOrgSlug;

    public SuperadminBootstrap(UserRepository userRepository, RoleRepository roleRepository,
                               OrganizationRepository organizationRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(defaultUsername)) return;
        Role role = roleRepository.findByCode("SUPERADMIN")
                .orElseThrow(() -> new IllegalStateException("SUPERADMIN role missing"));
        var organization = organizationRepository.findBySlug(defaultOrgSlug)
                .orElseThrow(() -> new IllegalStateException("Default organization missing"));
        User user = new User();
        user.setOrganizationId(organization.getId());
        user.setUsername(defaultUsername);
        user.setEmail(defaultUsername + "@sumicare.local");
        user.setPasswordHash(passwordEncoder.encode(defaultPassword));
        user.setRole(role);
        user.setDisplayName("Default Superadmin");
        user.setActive(true);
        userRepository.save(user);
    }
}
