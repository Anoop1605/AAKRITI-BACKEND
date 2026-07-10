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

    @PostMapping
    public ResponseEntity<?> registerTeam(
            @ModelAttribute TeamRegistrationDto registrationDto,
            @RequestParam("screenshot") MultipartFile screenshot) {

        try {
            log.info("Received registration request for category: {}", registrationDto.getCategory());

            byte[] screenshotBytes = screenshot.getBytes();
            String originalFilename = screenshot.getOriginalFilename();

            if (registrationDto.getCategory() == EventCategory.COMBO) {
                // 1. Process payment file and send targeted channel alerts to both groups
                telegramBotService.sendRegistrationAlert(registrationDto, screenshotBytes, originalFilename);

                // 2. Parse comboData JSON and append 12 rows to Google Sheets
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
                    
                    // Resolve category based on eventId prefix (starts with "cu-" or "cul-" is Culturals, otherwise Management)
                    String eventId = (String) roster.get("eventId");
                    if (eventId != null && (eventId.startsWith("cu-") || eventId.startsWith("cul-"))) {
                        eventDto.setCategory(EventCategory.CULTURALS);
                    } else {
                        eventDto.setCategory(EventCategory.MANAGEMENT);
                    }

                    comboDtos.add(eventDto);
                }
                sheetsService.appendComboRegistrations(comboDtos, "ComboPass");

                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.of("message", "Combo Pass Registration successful"));
            } else {
                // Standard single registration flow
                telegramBotService.sendRegistrationAlert(registrationDto, screenshotBytes, originalFilename);

                String screenshotStatusPlaceholder = "Sent to Telegram (" + 
                        (registrationDto.getCategory() != null ? registrationDto.getCategory().name() : "N/A") + ")";

                sheetsService.appendRegistration(registrationDto, screenshotStatusPlaceholder);

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
