/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.contact.repository;

import com.sumicare.contact.domain.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
    List<ContactMessage> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    List<ContactMessage> findAllByOrganizationIdAndCreatedAtBetweenOrderByCreatedAtAsc(UUID organizationId, OffsetDateTime from, OffsetDateTime to);
    List<ContactMessage> findAllByOrganizationIdAndReadAtIsNullOrderByCreatedAtDesc(UUID organizationId);
    long countByOrganizationIdAndReadAtIsNull(UUID organizationId);
    long countByIpAddressAndCreatedAtAfter(String ipAddress, OffsetDateTime since);
}
