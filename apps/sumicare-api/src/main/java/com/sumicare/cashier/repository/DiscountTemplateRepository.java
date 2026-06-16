/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.cashier.repository;

import com.sumicare.cashier.domain.DiscountTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiscountTemplateRepository extends JpaRepository<DiscountTemplate, UUID> {
    List<DiscountTemplate> findAllByOrganizationIdOrderByName(UUID organizationId);
}
