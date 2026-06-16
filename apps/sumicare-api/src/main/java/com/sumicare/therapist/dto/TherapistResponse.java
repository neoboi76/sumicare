/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.therapist.dto;

import java.util.UUID;

public record TherapistResponse(
        UUID id,
        String staffNumber,
        String nickname,
        String gender,
        boolean backup,
        boolean active,
        Long currentShiftId,
        String currentShiftLabel
) {}
