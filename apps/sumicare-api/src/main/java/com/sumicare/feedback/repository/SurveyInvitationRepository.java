/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.feedback.repository;

import com.sumicare.feedback.domain.SurveyInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SurveyInvitationRepository extends JpaRepository<SurveyInvitation, UUID> {

    Optional<SurveyInvitation> findByToken(String token);

    Optional<SurveyInvitation> findByOrderId(UUID orderId);
}
