/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.auth.dto;

public record LoginResponse(boolean mfaRequired, String challengeId, String email, TokenResponse token) {

    public static LoginResponse authenticated(TokenResponse token) {
        return new LoginResponse(false, null, null, token);
    }

    public static LoginResponse mfaChallenge(String challengeId, String maskedEmail) {
        return new LoginResponse(true, challengeId, maskedEmail, null);
    }
}
