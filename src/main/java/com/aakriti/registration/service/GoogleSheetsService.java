package com.aakriti.registration.service;

import com.aakriti.registration.dto.TeamRegistrationDto;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public void appendRegistration(TeamRegistrationDto dto, String screenshotUrl) throws IOException {
        String tabName = dto.getCategory().getSheetTabName();
        String range = tabName + "!A:K";

        // Generate unique Team ID
        String teamId = "TEAM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        List<Object> rowValues = Arrays.asList(
                teamId,
                dto.getTeamName(),
                dto.getEventName(),
                dto.getCollegeName(),
                dto.getYearOfStudy(),
                dto.getLeaderName(),
                dto.getLeaderEmail(),
                dto.getLeaderPhone(),
                dto.getMemberNames(),
                screenshotUrl,
                "Pending"
        );

        ValueRange body = new ValueRange()
                .setValues(Collections.singletonList(rowValues));

        log.info("Appending registration for team {} to sheet tab {}", dto.getTeamName(), tabName);

        sheetsService.spreadsheets().values()
                .append(SPREADSHEET_ID, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute();

        log.info("Successfully appended registration data for team {}", dto.getTeamName());
    }
}
