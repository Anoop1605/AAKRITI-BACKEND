package com.aakriti.registration.controller;

import com.aakriti.registration.dto.TeamRegistrationDto;
import com.aakriti.registration.model.EventCategory;
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

    // Resource-constrained thread pool tailored for Render Free Tier (512MB RAM, limited CPU share)
    private static final java.util.concurrent.ExecutorService registrationExecutor = 
        new java.util.concurrent.ThreadPoolExecutor(
            2, // Core size: 2 threads (fits our 2 parallel tasks)
            4, // Max size: 4 threads (prevents CPU thrashing and OOM)
            60L, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(50), // Bounded buffer to handle small registration bursts
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy() // Saturated pool fallback: runs task on HTTP worker thread
        );

    @GetMapping("/count")
    public ResponseEntity<?> getRegistrationCount(
            @RequestParam("category") String category,
            @RequestParam("eventName") String eventName) {
        try {
            log.info("Request for registration count: category={}, eventName={}", category, eventName);
            String cleanCategory = category.trim().toUpperCase();
            if (cleanCategory.equals("CULTURAL")) {
                cleanCategory = "CULTURALS";
            }
            EventCategory eventCategory = EventCategory.valueOf(cleanCategory);
            String tabName = eventCategory.getSheetTabName();
            int count = sheetsService.getRegistrationCount(tabName, eventName);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (IllegalArgumentException e) {
            log.error("Invalid category: {}", category, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid category: " + category));
        } catch (Exception e) {
            log.error("Failed to get registration count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get registration count: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> registerTeam(
            @ModelAttribute TeamRegistrationDto registrationDto,
            @RequestParam("screenshot") MultipartFile screenshot) {

        try {
            log.info("Received registration request for category: {}", registrationDto.getCategory());

            byte[] screenshotBytes = screenshot.getBytes();
            String originalFilename = screenshot.getOriginalFilename();

            // Generate and set Team ID on the DTO
            if (registrationDto.getCategory() == EventCategory.COMBO) {
                String comboTeamId = "COMBO-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                registrationDto.setTeamId(comboTeamId);
            } else {
                String teamId = "TEAM-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                registrationDto.setTeamId(teamId);
            }

            // 0. Pre-check for Free Fire capacity (to avoid starting threads if closed)
            if (registrationDto.getCategory() != EventCategory.COMBO && "Free Fire".equalsIgnoreCase(registrationDto.getEventName())) {
                int currentCount = sheetsService.getRegistrationCount("Culturals", "Free Fire");
                if (currentCount >= 12) {
                    log.warn("Free Fire registration rejected: 12 teams already registered.");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Registration closed: Free Fire is fully booked."));
                }
            }

            // 1. Run both Google Sheets and Telegram tasks in parallel threads using our bounded executor
            java.util.concurrent.CompletableFuture<Boolean> sheetsFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    if (registrationDto.getCategory() == EventCategory.COMBO) {
                        // Parse comboData JSON and build DTO list
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        java.util.List<java.util.Map<String, Object>> rosters = mapper.readValue(
                                registrationDto.getComboData(),
                                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {}
                        );

                        java.util.List<TeamRegistrationDto> comboDtos = new java.util.ArrayList<>();
                        for (java.util.Map<String, Object> roster : rosters) {
                            TeamRegistrationDto eventDto = new TeamRegistrationDto();
                            eventDto.setCollegeName(registrationDto.getCollegeName());
                            eventDto.setYearOfStudy(registrationDto.getYearOfStudy());
                            
                            String eventLeaderEmail = (String) roster.get("leaderEmail");
                            if (eventLeaderEmail == null || eventLeaderEmail.trim().isEmpty()) {
                                eventLeaderEmail = registrationDto.getLeaderEmail();
                            }
                            eventDto.setLeaderEmail(eventLeaderEmail);
                            
                            eventDto.setEventName((String) roster.get("eventName"));
                            eventDto.setTeamName((String) roster.get("teamName"));
                            eventDto.setLeaderName((String) roster.get("leaderName"));
                            eventDto.setLeaderPhone((String) roster.get("leaderPhone"));
                            eventDto.setMemberNames((String) roster.get("memberNames"));
                            
                            String eventId = (String) roster.get("eventId");
                            if (eventId != null && (eventId.startsWith("cu-") || eventId.startsWith("cul-"))) {
                                eventDto.setCategory(EventCategory.CULTURALS);
                            } else {
                                eventDto.setCategory(EventCategory.MANAGEMENT);
                            }

                            eventDto.setAmountPaid(registrationDto.getAmountPaid() != null ? registrationDto.getAmountPaid() : "3540");

                            // Set unique teamId for each sub-registration in the combo
                            String eventTeamId = "TEAM-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                            eventDto.setTeamId(eventTeamId);

                            comboDtos.add(eventDto);
                        }

                        return sheetsService.appendComboRegistrations(comboDtos, "ComboPass");
                    } else {
                        String screenshotStatusPlaceholder = "Sent to Telegram (" + 
                                (registrationDto.getCategory() != null ? registrationDto.getCategory().name() : "N/A") + ")";

                        return sheetsService.appendRegistration(registrationDto, screenshotStatusPlaceholder);
                    }
                } catch (Exception e) {
                    log.error("Google Sheets write execution failed in thread", e);
                    return false;
                }
            }, registrationExecutor);

            java.util.concurrent.CompletableFuture<Boolean> telegramFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return telegramBotService.sendRegistrationAlert(registrationDto, screenshotBytes, originalFilename);
                } catch (Exception e) {
                    log.error("Telegram alert execution failed in thread", e);
                    return false;
                }
            }, registrationExecutor);

            // 2. Wait for both threads to finish simultaneously
            java.util.concurrent.CompletableFuture.allOf(sheetsFuture, telegramFuture).join();

            boolean sheetsSuccess = false;
            boolean telegramSuccess = false;
            try {
                sheetsSuccess = sheetsFuture.get();
                telegramSuccess = telegramFuture.get();
            } catch (Exception e) {
                log.error("Error retrieving results from parallel futures", e);
            }

            // 3. Process results and fallback warnings
            if (!sheetsSuccess && telegramSuccess) {
                // Google Sheets failed, but Telegram succeeded (backup warning sent)
                try {
                    telegramBotService.sendGoogleSheetsFailureAlert(registrationDto, "All connection attempts to Google Sheets timed out/failed.");
                } catch (Exception e) {
                    log.error("Failed to send Google Sheets failure alert to Telegram", e);
                }

                if (registrationDto.getCategory() == EventCategory.COMBO) {
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(Map.of("message", "Combo Pass Registration successful (Warning: Saved to Telegram backup)"));
                } else {
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(Map.of("message", "Registration successful (Warning: Saved to Telegram backup)"));
                }
            }

            if (!telegramSuccess && sheetsSuccess) {
                // Telegram failed but Sheets succeeded. We return success but log severe warning.
                log.error("CRITICAL: Telegram alert failed but Google Sheets succeeded. Registration data is saved but screenshot is lost!");
                if (registrationDto.getCategory() == EventCategory.COMBO) {
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(Map.of("message", "Combo Pass Registration successful"));
                } else {
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(Map.of("message", "Registration successful", "teamName", registrationDto.getTeamName()));
                }
            }

            if (!telegramSuccess && !sheetsSuccess) {
                // BOTH failed. Return a 500 error.
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to process registration: Both Telegram and Google Sheets write operations failed. Please try again."));
            }

            // BOTH succeeded. Return success.
            if (registrationDto.getCategory() == EventCategory.COMBO) {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.of("message", "Combo Pass Registration successful"));
            } else {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.of("message", "Registration successful", "teamName", registrationDto.getTeamName()));
            }

        } catch (Exception e) {
            log.error("Failed to process registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process registration: " + e.getMessage()));
        }
    }
}
