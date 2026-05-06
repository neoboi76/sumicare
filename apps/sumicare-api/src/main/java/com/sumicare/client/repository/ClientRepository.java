package com.sumicare.client.repository;

import com.sumicare.client.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findByOrganizationIdAndNickname(UUID organizationId, String nickname);
    Optional<Client> findByOrganizationIdAndEmail(UUID organizationId, String email);
    boolean existsByOrganizationIdAndNickname(UUID organizationId, String nickname);
}
