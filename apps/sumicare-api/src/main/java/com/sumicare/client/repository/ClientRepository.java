package com.sumicare.client.repository;

import com.sumicare.client.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findByOrganizationIdAndEmailAndDeletedAtIsNull(UUID organizationId, String email);
    boolean existsByOrganizationIdAndEmailIgnoreCaseAndDeletedAtIsNull(UUID organizationId, String email);
    List<Client> findAllByOrganizationIdAndDeletedAtIsNullOrderByNicknameAsc(UUID organizationId);
    List<Client> findTop20ByOrganizationIdAndNicknameContainingIgnoreCaseAndDeletedAtIsNullOrderByNicknameAsc(UUID organizationId, String nicknameFragment);
}
