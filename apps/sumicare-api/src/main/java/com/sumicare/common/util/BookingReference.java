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
