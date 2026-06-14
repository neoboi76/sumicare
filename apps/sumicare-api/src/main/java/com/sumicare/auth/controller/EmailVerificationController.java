package com.sumicare.auth.controller;

import com.sumicare.auth.domain.EmailVerificationToken;
import com.sumicare.auth.repository.EmailVerificationTokenRepository;
import com.sumicare.common.config.AppProperties;
import com.sumicare.user.domain.User;
import com.sumicare.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;

    public EmailVerificationController(EmailVerificationTokenRepository tokenRepository,
                                       UserRepository userRepository,
                                       AppProperties appProperties) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.appProperties = appProperties;
    }

    @GetMapping("/verify")
    @Transactional
    public org.springframework.http.ResponseEntity<Void> verify(@RequestParam("token") String token) {
        EmailVerificationToken evToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid token"));

        if (evToken.isConsumed()) {
            return redirect("/sumicare/login?verified=already");
        }
        if (evToken.isExpired()) {
            return redirect("/sumicare/login?verified=expired");
        }

        evToken.setConsumedAt(OffsetDateTime.now());
        tokenRepository.save(evToken);

        User user = userRepository.findById(evToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setEmailVerified(true);
        userRepository.save(user);

        return redirect("/sumicare/login?verified=1");
    }

    private org.springframework.http.ResponseEntity<Void> redirect(String path) {
        return org.springframework.http.ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(appProperties.app().publicBaseUrl() + path))
                .build();
    }
}
