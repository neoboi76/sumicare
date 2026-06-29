/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.room.exception;

public class RoomGenderConflictException extends RuntimeException {
    public RoomGenderConflictException(String existingGender) {
        super("Room is already occupied by " + existingGender + "; cannot mix genders in this room.");
    }
}
