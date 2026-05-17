package com.sumicare.auth.repository;

import com.sumicare.auth.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findByTokenAndTokenType(String token, String tokenType);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.userId = :userId")
    void deleteAllByUserId(UUID userId);
}
