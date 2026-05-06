package com.sumicare.service_catalogue.repository;

import com.sumicare.service_catalogue.domain.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServiceRepository extends JpaRepository<Service, Long> {
    List<Service> findAllByOrganizationIdAndActiveTrue(UUID organizationId);
}
