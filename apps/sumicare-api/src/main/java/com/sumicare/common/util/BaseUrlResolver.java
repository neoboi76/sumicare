/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.common.util;

import com.sumicare.common.config.AppProperties;
import org.springframework.stereotype.Component;

@Component
public class BaseUrlResolver {

    private static final String DEFAULT_PUBLIC_BASE_URL = "https://newlasemaspa.up.railway.app";

    private final AppProperties appProperties;

    public BaseUrlResolver(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String resolve() {
        String configured = appProperties.app().publicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.trim().replaceAll("[./]+$", "");
        }
        return DEFAULT_PUBLIC_BASE_URL;
    }
}
