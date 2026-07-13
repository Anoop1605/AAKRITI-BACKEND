package com.aakriti.registration.service;

import com.aakriti.registration.dto.TeamRegistrationDto;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsService {

    private final Sheets sheetsService;
    // Spreadsheet ID parsed from the user's prompt
    private static final String SPREADSHEET_ID = "10n22yio_eOvUpVC3GDmHLejZvIaZqsHgRYOroeICU4k";

    public boolean appendRegistration(TeamRegistrationDto dto, String screenshotUrl) {
        int maxRetries = 3;
        for (int i = 1; i <= maxRetries; i++) {
            try {
                appendRegistrationSync(dto, screenshotUrl);
                return true;
            } catch (IOException e) {
                log.warn("Attempt {} of {} failed to write to Google Sheets for team {}: {}", 
                        i, maxRetries, dto.getTeamName(), e.getMessage());
                if (i < maxRetries) {
                    try {
                        Thread.sleep(500 * i);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("All {} attempts to write to Google Sheets failed for team {}", maxRetries, dto.getTeamName(), e);
                }
            }
        }
        return false;
    }

    public boolean appendComboRegistrations(List<TeamRegistrationDto> dtos, String screenshotUrl) {
        log.info("Starting batch grouped sync append for {} combo pass events", dtos.size());
        
        // Group by tab name to minimize API calls
        java.util.Map<String, List<TeamRegistrationDto>> groupedByTab = new java.util.HashMap<>();
        for (TeamRegistrationDto dto : dtos) {
            String tabName = dto.getCategory().getSheetTabName();
            groupedByTab.computeIfAbsent(tabName, k -> new java.util.ArrayList<>()).add(dto);
        }

        boolean allSucceeded = true;
        for (java.util.Map.Entry<String, List<TeamRegistrationDto>> entry : groupedByTab.entrySet()) {
            String tabName = entry.getKey();
            List<TeamRegistrationDto> groupDtos = entry.getValue();

            int maxRetries = 3;
            boolean success = false;
            for (int i = 1; i <= maxRetries; i++) {
                try {
                    appendRegistrationsBatchSync(groupDtos, tabName, screenshotUrl);
                    success = true;
                    break;
                } catch (IOException e) {
                    log.warn("Attempt {} of {} failed to batch write to tab {}: {}", i, maxRetries, tabName, e.getMessage());
                    if (i < maxRetries) {
                        try {
                            Thread.sleep(500 * i);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        log.error("All {} attempts to batch write to tab {} failed.", maxRetries, tabName, e);
                    }
                }
            }
            if (!success) {
                allSucceeded = false;
            }
        }
        return allSucceeded;
    }

    public void appendRegistrationSync(TeamRegistrationDto dto, String screenshotUrl) throws IOException {
        String tabName = dto.getCategory().getSheetTabName();
        String range = tabName + "!A:L";

        // Use pre-generated Team ID if present, otherwise generate a new one
        String teamId = dto.getTeamId();
        if (teamId == null || teamId.trim().isEmpty()) {
            teamId = "TEAM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            dto.setTeamId(teamId);
        }

        String teamNameFallback = dto.getTeamName();
        if (teamNameFallback == null || teamNameFallback.trim().isEmpty()) {
            teamNameFallback = "Solo";
        }

        List<Object> rowValues = Arrays.asList(
                teamId,
                teamNameFallback,
                dto.getEventName(),
                dto.getCollegeName(),
                dto.getYearOfStudy(),
                dto.getLeaderName(),
                dto.getLeaderEmail(),
                dto.getLeaderPhone(),
                dto.getMemberNames(),
                screenshotUrl,
                "Pending",
                dto.getAmountPaid() != null ? dto.getAmountPaid() : ""
        );

        ValueRange body = new ValueRange()
                .setValues(Collections.singletonList(rowValues));

        log.info("Appending registration for team {} to sheet tab {}", teamNameFallback, tabName);

        sheetsService.spreadsheets().values()
                .append(SPREADSHEET_ID, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute();

        log.info("Successfully appended registration data for team {}", teamNameFallback);
    }

    public void appendRegistrationsBatchSync(List<TeamRegistrationDto> dtos, String tabName, String screenshotUrl) throws IOException {
        String range = tabName + "!A:L";
        List<List<Object>> allRowValues = new java.util.ArrayList<>();

        for (TeamRegistrationDto dto : dtos) {
            String teamId = dto.getTeamId();
            if (teamId == null || teamId.trim().isEmpty()) {
                teamId = "TEAM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                dto.setTeamId(teamId);
            }
            String teamNameFallback = dto.getTeamName();
            if (teamNameFallback == null || teamNameFallback.trim().isEmpty()) {
                teamNameFallback = "Solo";
            }

            List<Object> rowValues = Arrays.asList(
                    teamId,
                    teamNameFallback,
                    dto.getEventName(),
                    dto.getCollegeName(),
                    dto.getYearOfStudy(),
                    dto.getLeaderName(),
                    dto.getLeaderEmail(),
                    dto.getLeaderPhone(),
                    dto.getMemberNames(),
                    screenshotUrl,
                    "Pending",
                    dto.getAmountPaid() != null ? dto.getAmountPaid() : ""
            );
            allRowValues.add(rowValues);
        }

        ValueRange body = new ValueRange().setValues(allRowValues);

        log.info("Batch appending {} registrations to sheet tab {}", dtos.size(), tabName);
        
        sheetsService.spreadsheets().values()
                .append(SPREADSHEET_ID, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute();
                
        log.info("Successfully batch appended {} registrations to sheet tab {}", dtos.size(), tabName);
    }

    public int getRegistrationCount(String tabName, String eventName) {
        try {
            String range = tabName + "!A:L";
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(SPREADSHEET_ID, range)
                    .execute();
            List<List<Object>> values = response.getValues();
            if (values == null || values.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (List<Object> row : values) {
                if (row.size() > 2 && row.get(2) != null && row.get(2).toString().trim().equalsIgnoreCase(eventName.trim())) {
                    count++;
                }
            }
            log.info("Counted {} registrations for event {} in tab {}", count, eventName, tabName);
            return count;
        } catch (IOException e) {
            log.error("Failed to fetch registration count from Google Sheets for tab: {}, event: {}", tabName, eventName, e);
            return 0;
        }
    }
}
