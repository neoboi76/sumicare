package com.sumicare.user.bootstrap;

import com.sumicare.organization.domain.Organization;
import com.sumicare.organization.repository.OrganizationRepository;
import com.sumicare.user.domain.Role;
import com.sumicare.user.domain.User;
import com.sumicare.user.repository.RoleRepository;
import com.sumicare.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Order(20)
public class TestUsersBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${sumicare.bootstrap.defaultOrganizationSlug:lasema}")
    private String defaultOrgSlug;

    @Value("${sumicare.bootstrap.seedTestUsers:true}")
    private boolean seedTestUsers;

    public TestUsersBootstrap(UserRepository userRepository, RoleRepository roleRepository,
                              OrganizationRepository organizationRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private record TestUserSpec(String username, String email, String displayName, String roleCode, String password) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedTestUsers) return;
        Optional<Organization> organization = organizationRepository.findBySlug(defaultOrgSlug);
        if (organization.isEmpty()) return;

        List<TestUserSpec> specs = List.of(
                new TestUserSpec("testadmin", "testadmin@lasema.test", "Test Admin", "ADMIN", "TestAdmin!12345"),
                new TestUserSpec("testmanager", "testmanager@lasema.test", "Test Manager", "MANAGER", "TestManager!12345"),
                new TestUserSpec("testreceptionist", "testreceptionist@lasema.test", "Test Receptionist", "RECEPTIONIST", "TestReceptionist!12345"),
                new TestUserSpec("teststaff", "teststaff@lasema.test", "Test Staff", "STAFF", "TestStaff!12345")
        );

        for (TestUserSpec spec : specs) {
            Optional<Role> role = roleRepository.findByCode(spec.roleCode());
            if (role.isEmpty()) continue;

            Optional<User> existing = userRepository.findByUsername(spec.username());
            if (existing.isPresent()) {
                User user = existing.get();
                boolean changed = false;
                if (!passwordEncoder.matches(spec.password(), user.getPasswordHash())) {
                    user.setPasswordHash(passwordEncoder.encode(spec.password()));
                    changed = true;
                }
                if (!user.isActive()) { user.setActive(true); changed = true; }
                if (!user.isEmailVerified()) { user.setEmailVerified(true); changed = true; }
                if (changed) userRepository.save(user);
            } else {
                User user = new User();
                user.setOrganizationId(organization.get().getId());
                user.setUsername(spec.username());
                user.setEmail(spec.email());
                user.setPasswordHash(passwordEncoder.encode(spec.password()));
                user.setRole(role.get());
                user.setDisplayName(spec.displayName());
                user.setActive(true);
                user.setEmailVerified(true);
                userRepository.save(user);
            }
        }
    }
}
