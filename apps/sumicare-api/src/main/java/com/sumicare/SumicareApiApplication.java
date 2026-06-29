/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SumicareApiApplication {

    public static void main(String[] args) {
        // Pin the JVM default to the spa's timezone so JDBC timestamps and report day
        // windows align with the local business day rather than the host's zone.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"));
        SpringApplication.run(SumicareApiApplication.class, args);
    }
}
