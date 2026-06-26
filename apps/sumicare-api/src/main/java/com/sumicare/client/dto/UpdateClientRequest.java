/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.client.dto;

import jakarta.validation.constraints.Size;

public record UpdateClientRequest(
        @Size(max = 120) String nickname,
        @Size(max = 8) String gender,
        @Size(max = 120) String nationality
) {}
