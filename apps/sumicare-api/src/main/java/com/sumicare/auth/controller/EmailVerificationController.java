/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.auth.controller;

import com.sumicare.auth.domain.EmailVerificationToken;
import com.sumicare.auth.repository.EmailVerificationTokenRepository;
import com.sumicare.common.util.BaseUrlResolver;
import com.sumicare.user.domain.User;
import com.sumicare.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final BaseUrlResolver baseUrlResolver;

    public EmailVerificationController(EmailVerificationTokenRepository tokenRepository,
                                       UserRepository userRepository,
                                       BaseUrlResolver baseUrlResolver) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.baseUrlResolver = baseUrlResolver;
    }

    @GetMapping("/verify")
    @Transactional
    public ResponseEntity<Void> verify(@RequestParam("token") String token) {
        Optional<EmailVerificationToken> found = tokenRepository.findByToken(token);
        if (found.isEmpty()) {
            return redirect("/sumicare/login?verified=invalid");
        }

        EmailVerificationToken evToken = found.get();
        if (evToken.isConsumed()) {
            return redirect("/sumicare/login?verified=already");
        }
        if (evToken.isExpired()) {
            return redirect("/sumicare/login?verified=expired");
        }

        evToken.setConsumedAt(OffsetDateTime.now());
        tokenRepository.save(evToken);

        Optional<User> user = userRepository.findById(evToken.getUserId());
        if (user.isEmpty()) {
            return redirect("/sumicare/login?verified=invalid");
        }
        user.get().setEmailVerified(true);
        userRepository.save(user.get());

        return redirect("/sumicare/login?verified=1");
    }

    private ResponseEntity<Void> redirect(String path) {
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(baseUrlResolver.resolve() + path))
                .build();
    }
}
