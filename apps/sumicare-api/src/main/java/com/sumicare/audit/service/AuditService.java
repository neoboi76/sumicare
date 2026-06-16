/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.audit.service;

import com.sumicare.audit.domain.AuditLog;
import com.sumicare.audit.repository.AuditLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Async
    public void record(UUID organizationId, UUID actorUserId, String actorRole,
                       String actionType, String targetEntity, String targetId,
                       String metadata, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setOrganizationId(organizationId);
        log.setActorUserId(actorUserId);
        log.setActorRole(actorRole);
        log.setActionType(actionType);
        log.setTargetEntity(targetEntity);
        log.setTargetId(targetId);
        log.setMetadata(metadata);
        log.setIpAddress(ipAddress);
        repository.save(log);
    }
}
