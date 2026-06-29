/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.therapist.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTherapistRequest(
        String staffNumber,
        @NotBlank String nickname,
        @NotBlank String gender,
        boolean backup,
        Long shiftId
) {}
