/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.therapist.dto;

import java.util.UUID;

public record LineupTherapistResponse(
        UUID therapistId,
        String nickname,
        String gender,
        String shiftLabel,
        String flag,
        boolean skipped,
        int position,
        boolean onCall
) {}
