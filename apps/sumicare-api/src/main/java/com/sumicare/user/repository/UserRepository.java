/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.user.repository;

import com.sumicare.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findAllByOrganizationId(UUID organizationId);
    List<User> findAllByOrganizationIdAndActiveTrue(UUID organizationId);
    List<User> findAllByOrganizationIdAndActiveFalse(UUID organizationId);
    boolean existsByUsername(String username);
    boolean existsByEmailIgnoreCase(String email);
}
