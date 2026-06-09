package com.sumicare.auth.email;

public record Attachment(String filename, String contentType, byte[] content) {}
