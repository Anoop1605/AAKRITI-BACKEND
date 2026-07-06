package com.aakriti.registration.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

@Configuration
@Slf4j
public class GoogleConfig {

    private static final String APPLICATION_NAME = "Aakriti-Registration";
    private static final List<String> SCOPES = Arrays.asList(
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/spreadsheets"
    );

    @Bean
    public GoogleCredentials googleCredentials() {
        try (InputStream credentialsStream = getClass().getResourceAsStream("/google-credentials.json")) {
            if (credentialsStream == null) {
                log.warn("google-credentials.json not found in resources. Fallback to unauthenticated dummy credentials.");
                return com.google.auth.oauth2.GoogleCredentials.create(new com.google.auth.oauth2.AccessToken("dummy-token", null)).createScoped(SCOPES);
            }
            return GoogleCredentials.fromStream(credentialsStream).createScoped(SCOPES);
        } catch (Exception e) {
            log.error("Failed to load Google credentials. Using unauthenticated fallback. (Ensure google-credentials.json is placed in src/main/resources or GOOGLE_APPLICATION_CREDENTIALS is set): {}", e.getMessage());
            return com.google.auth.oauth2.GoogleCredentials.create(new com.google.auth.oauth2.AccessToken("dummy-token", null)).createScoped(SCOPES);
        }
    }

    @Bean
    public Drive googleDrive(GoogleCredentials credentials) throws GeneralSecurityException, IOException {
        if (credentials == null) {
            return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    null
            ).setApplicationName(APPLICATION_NAME).build();
        }
        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        )
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    @Bean
    public Sheets googleSheets(GoogleCredentials credentials) throws GeneralSecurityException, IOException {
        if (credentials == null) {
            return new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    null
            ).setApplicationName(APPLICATION_NAME).build();
        }
        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        )
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
