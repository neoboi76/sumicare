package com.sumicare.content.repository;

import com.sumicare.content.domain.WebsiteContentBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebsiteContentBlockRepository extends JpaRepository<WebsiteContentBlock, UUID> {
    List<WebsiteContentBlock> findAllByOrganizationIdAndPublishedTrueOrderByDisplayOrderAsc(UUID organizationId);
    List<WebsiteContentBlock> findAllByOrganizationIdOrderByDisplayOrderAsc(UUID organizationId);
}
