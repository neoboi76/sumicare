/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

package com.sumicare.feedback.controller;

import com.sumicare.feedback.dto.SubmitSurveyRequest;
import com.sumicare.feedback.dto.SurveyDetailResponse;
import com.sumicare.feedback.service.SurveyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/survey")
public class SurveyController {

    private final SurveyService surveyService;

    public SurveyController(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    @GetMapping("/{token}")
    public SurveyDetailResponse get(@PathVariable String token) {
        return surveyService.getSurvey(token);
    }

    @PostMapping("/{token}")
    public ResponseEntity<Void> submit(@PathVariable String token, @Valid @RequestBody SubmitSurveyRequest request) {
        surveyService.submitSurvey(token, request);
        return ResponseEntity.ok().build();
    }
}
