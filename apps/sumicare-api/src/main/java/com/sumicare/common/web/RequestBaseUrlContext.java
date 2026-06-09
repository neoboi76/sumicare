package com.sumicare.common.web;

public final class RequestBaseUrlContext {

    private static final ThreadLocal<String> ORIGIN = new ThreadLocal<>();

    private RequestBaseUrlContext() {
    }

    public static void set(String origin) {
        ORIGIN.set(origin);
    }

    public static String get() {
        return ORIGIN.get();
    }

    public static void clear() {
        ORIGIN.remove();
    }
}
