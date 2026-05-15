package com.sumicare.cashier.repository;

import com.sumicare.cashier.domain.DiscountTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiscountTemplateRepository extends JpaRepository<DiscountTemplate, UUID> {
    List<DiscountTemplate> findAllByOrganizationIdOrderByName(UUID organizationId);
}
