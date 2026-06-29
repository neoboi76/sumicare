/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.common.util;

import java.util.UUID;

public final class BookingReference {

    private BookingReference() {
    }

    public static String of(UUID id) {
        if (id == null) {
            return null;
        }
        return id.toString().substring(0, 8).toUpperCase();
    }
}
