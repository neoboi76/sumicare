package com.sumicare.auth.dto;

public record ResetPasswordRequest(String token, String newPassword) {}
