package com.sumicare.room.exception;

public class RoomGenderConflictException extends RuntimeException {
    public RoomGenderConflictException(String existingGender) {
        super("Room is already occupied by " + existingGender + "; cannot mix genders in this room.");
    }
}
