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

    @Async
    public void appendRegistration(TeamRegistrationDto dto, String screenshotUrl) {
        appendRegistrationSync(dto, screenshotUrl);
    }

    @Async
    public void appendComboRegistrations(List<TeamRegistrationDto> dtos, String screenshotUrl) {
        log.info("Starting sequential async append for {} combo pass events", dtos.size());
        for (TeamRegistrationDto dto : dtos) {
            appendRegistrationSync(dto, screenshotUrl);
            try {
                // Sleep briefly to prevent Google API rate limits and concurrent write lock clashes
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Combo pass registration append loop interrupted");
                break;
            }
        }
        log.info("Finished sequential combo pass append");
    }

    public void appendRegistrationSync(TeamRegistrationDto dto, String screenshotUrl) {
        try {
            String tabName = dto.getCategory().getSheetTabName();
            String range = tabName + "!A:L";

            // Generate unique Team ID
            String teamId = "TEAM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

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
        } catch (IOException e) {
            log.error("Failed to append registration data to Google Sheets", e);
        }
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
