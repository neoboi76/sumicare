package com.sumicare.common.util;

import com.sumicare.common.config.AppProperties;
import com.sumicare.common.web.RequestBaseUrlContext;
import org.springframework.stereotype.Component;

@Component
public class BaseUrlResolver {

    private final AppProperties appProperties;

    public BaseUrlResolver(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String resolve() {
        String configured = appProperties.app().publicBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.replaceAll("/+$", "");
        }
        String fromRequest = RequestBaseUrlContext.get();
        return fromRequest == null ? "" : fromRequest;
    }
}
