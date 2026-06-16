/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.common.util;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdSequenceService {

    private final JdbcTemplate jdbcTemplate;

    public IdSequenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long next(String sequenceName) {
        Long value = jdbcTemplate.queryForObject("SELECT nextval(?)", Long.class, sequenceName);
        return value == null ? 0L : value;
    }

    public String nextOrNumber() {
        return "OR" + String.format("%08d", next("or_number_seq"));
    }

    public String nextReceiptNumber() {
        return "RCPT" + String.format("%08d", next("receipt_number_seq"));
    }

    public String nextTsn() {
        String digits = String.format("%06d", next("tsn_seq") % 1_000_000L);
        return digits.substring(0, 3) + "-" + digits.substring(3);
    }
}
