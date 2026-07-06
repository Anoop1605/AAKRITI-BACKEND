package com.aakriti.registration.controller;

import com.aakriti.registration.dto.TeamRegistrationDto;
import com.aakriti.registration.service.TelegramBotService;
import com.aakriti.registration.service.GoogleSheetsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/registrations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class RegistrationController {

    private final TelegramBotService telegramBotService;
    private final GoogleSheetsService sheetsService;

    @PostMapping
    public ResponseEntity<?> registerTeam(
            @ModelAttribute TeamRegistrationDto registrationDto,
            @RequestParam("screenshot") MultipartFile screenshot) {

        try {
            log.info("Received registration request for team: {}", registrationDto.getTeamName());

            // Extract screenshot bytes and filename synchronously before firing async task
            byte[] screenshotBytes = screenshot.getBytes();
            String originalFilename = screenshot.getOriginalFilename();

            // 1. Process payment file and send instant targeted channel alerts asynchronously
            telegramBotService.sendRegistrationAlert(registrationDto, screenshotBytes, originalFilename);

            // 2. Instead of a direct drive URL, save a placeholder flag reference in your Google Sheet column
            String screenshotStatusPlaceholder = "Sent to Telegram (" + 
                    (registrationDto.getCategory() != null ? registrationDto.getCategory().name() : "N/A") + ")";

            // 3. Commit row structures right down to Google Sheets database asynchronously
            sheetsService.appendRegistration(registrationDto, screenshotStatusPlaceholder);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Registration successful", "teamName", registrationDto.getTeamName()));

        } catch (Exception e) {
            log.error("Failed to process registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process registration: " + e.getMessage()));
        }
    }
}
