/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.content.repository;

import com.sumicare.content.domain.WebsiteContentBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebsiteContentBlockRepository extends JpaRepository<WebsiteContentBlock, UUID> {
    List<WebsiteContentBlock> findAllByOrganizationIdAndPublishedTrueOrderByDisplayOrderAsc(UUID organizationId);
    List<WebsiteContentBlock> findAllByOrganizationIdOrderByDisplayOrderAsc(UUID organizationId);
}
