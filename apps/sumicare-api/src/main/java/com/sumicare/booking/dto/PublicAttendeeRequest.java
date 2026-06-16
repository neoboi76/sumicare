/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.booking.dto;

import jakarta.validation.constraints.Size;

public record PublicAttendeeRequest(
        Long packageTierId,
        @Size(max = 16) String lockerNumber,
        @Size(max = 1) String clientGender
) {}
