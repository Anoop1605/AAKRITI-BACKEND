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
        // 1. Try to load from environment variable
        String envJson = System.getenv("GOOGLE_CREDENTIALS_JSON");
        if (envJson != null && !envJson.trim().isEmpty()) {
            try {
                log.info("Loading Google credentials from GOOGLE_CREDENTIALS_JSON environment variable");
                return GoogleCredentials.fromStream(new java.io.ByteArrayInputStream(envJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .createScoped(SCOPES);
            } catch (Exception e) {
                log.error("Failed to load Google credentials from environment variable: {}", e.getMessage());
            }
        }

        // 2. Try to load from classpath
        try (InputStream credentialsStream = getClass().getResourceAsStream("/google-credentials.json")) {
            if (credentialsStream != null) {
                log.info("Loading Google credentials from classpath: /google-credentials.json");
                return GoogleCredentials.fromStream(credentialsStream).createScoped(SCOPES);
            }
        } catch (Exception e) {
            log.warn("Failed to load Google credentials from classpath: {}", e.getMessage());
        }

        // 3. Try to load from file in the root directory
        java.io.File file = new java.io.File("google-credentials.json");
        if (file.exists()) {
            try (InputStream fileStream = new java.io.FileInputStream(file)) {
                log.info("Loading Google credentials from root directory file: google-credentials.json");
                return GoogleCredentials.fromStream(fileStream).createScoped(SCOPES);
            } catch (Exception e) {
                log.error("Failed to load Google credentials from root directory file: {}", e.getMessage());
            }
        }

        log.warn("google-credentials.json not found in env, classpath, or root. Fallback to unauthenticated dummy credentials.");
        return com.google.auth.oauth2.GoogleCredentials.create(new com.google.auth.oauth2.AccessToken("dummy-token", null)).createScoped(SCOPES);
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
