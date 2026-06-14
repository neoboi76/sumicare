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
            return configured.replaceAll("/+$", "");
        }
        return DEFAULT_PUBLIC_BASE_URL;
    }
}
