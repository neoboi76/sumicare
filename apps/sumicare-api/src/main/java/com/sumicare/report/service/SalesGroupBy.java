/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.report.service;

public enum SalesGroupBy {
    SERVICE,
    PACKAGE,
    THERAPIST,
    COMMISSION,
    PAYMENT_METHOD,
    ROOM_TYPE,
    VOUCHER,
    LEDGER_ACCOUNT;

    public String displayLabel() {
        return switch (this) {
            case SERVICE -> "Service";
            case PACKAGE -> "Package";
            case THERAPIST -> "Therapist";
            case COMMISSION -> "Commission";
            case PAYMENT_METHOD -> "Payment Method";
            case ROOM_TYPE -> "Room Type";
            case VOUCHER -> "Voucher";
            case LEDGER_ACCOUNT -> "Ledger Account";
        };
    }
}
