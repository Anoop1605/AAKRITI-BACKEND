package com.aakriti.registration.service;

import com.aakriti.registration.dto.TeamRegistrationDto;
import com.aakriti.registration.model.EventCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class TelegramBotService {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.chat-id.sports}")
    private String sportsChatId;

    @Value("${telegram.chat-id.cultural}")
    private String culturalChatId;

    @Value("${telegram.chat-id.management}")
    private String managementChatId;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean sendRegistrationAlert(TeamRegistrationDto dto, byte[] screenshotBytes, String originalFilename) {
        if (dto.getCategory() == EventCategory.COMBO) {
            log.info("Sending COMBO PASS registration alert to both Cultural and Management groups.");
            boolean success1 = sendToChannel(dto, screenshotBytes, originalFilename, culturalChatId);
            boolean success2 = sendToChannel(dto, screenshotBytes, originalFilename, managementChatId);
            return success1 || success2;
        } else {
            String targetChatId = determineChatId(dto.getCategory());
            return sendToChannel(dto, screenshotBytes, originalFilename, targetChatId);
        }
    }

    private boolean sendToChannel(TeamRegistrationDto dto, byte[] screenshotBytes, String originalFilename, String targetChatId) {
        String caption = formatTelegramMessage(dto);
        String url = "https://api.telegram.org/bot" + botToken + "/sendPhoto";

        try {
            ByteArrayResource fileResource = new ByteArrayResource(screenshotBytes) {
                @Override
                public String getFilename() {
                    return originalFilename != null ? originalFilename : "receipt.jpg";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", targetChatId);
            body.add("photo", fileResource);
            body.add("caption", caption);
            body.add("parse_mode", "Markdown");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            log.info("Telegram notification sent successfully to {}. Status code: {}", targetChatId, response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            log.error("Failed to send notification message to Telegram channel " + targetChatId, e);
            return false;
        }
    }

    public void sendGoogleSheetsFailureAlert(TeamRegistrationDto dto, String errorMessage) {
        String targetChatId = (dto.getCategory() == EventCategory.COMBO) ? culturalChatId : determineChatId(dto.getCategory());
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ *SYSTEM WARNING: GOOGLE SHEETS WRITE FAILED* ⚠️\n");
        sb.append("----------------------------------------\n");
        sb.append("The registration below was successfully received, but *failed to write to Google Sheets*.\n");
        sb.append("Please add this record manually:\n\n");
        
        if (dto.getCategory() == EventCategory.COMBO) {
            sb.append("👑 *Master Leader:* ").append(dto.getLeaderName()).append("\n");
            sb.append("📧 *Email:* ").append(dto.getLeaderEmail()).append("\n");
            sb.append("📞 *Phone:* ").append(dto.getLeaderPhone()).append("\n");
            sb.append("🏛️ *Institution:* ").append(dto.getCollegeName()).append("\n");
            sb.append("🎓 *Year:* ").append(dto.getYearOfStudy()).append("\n");
            sb.append("💰 *Amount:* ").append(dto.getAmountPaid() != null ? dto.getAmountPaid() : "3540").append("\n");
            sb.append("📦 *Combo Data:* ").append(dto.getComboData()).append("\n");
        } else {
            sb.append("🏆 *Team Name:* `").append(dto.getTeamName() != null && !dto.getTeamName().trim().isEmpty() ? dto.getTeamName() : "Solo").append("`\n");
            sb.append("✨ *Event Name:* ").append(dto.getEventName()).append("\n");
            sb.append("👑 *Leader Name:* ").append(dto.getLeaderName()).append("\n");
            sb.append("📧 *Email:* ").append(dto.getLeaderEmail()).append("\n");
            sb.append("📞 *Phone:* ").append(dto.getLeaderPhone()).append("\n");
            sb.append("🏛️ *Institution:* ").append(dto.getCollegeName()).append("\n");
            sb.append("🎓 *Year:* ").append(dto.getYearOfStudy()).append("\n");
            sb.append("👥 *Members:* ").append(dto.getMemberNames() != null && !dto.getMemberNames().isEmpty() ? dto.getMemberNames() : "Solo").append("\n");
            sb.append("💰 *Amount:* ").append(dto.getAmountPaid() != null ? dto.getAmountPaid() : "N/A").append("\n");
        }
        sb.append("\n❌ *Error Details:* ").append(errorMessage).append("\n");
        sb.append("----------------------------------------\n");

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", targetChatId);
            body.add("text", sb.toString());
            body.add("parse_mode", "Markdown");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, requestEntity, String.class);
            log.info("Google Sheets failure alert sent to Telegram channel {}", targetChatId);
            
            if (dto.getCategory() == EventCategory.COMBO) {
                body.set("chat_id", managementChatId);
                HttpEntity<MultiValueMap<String, Object>> requestEntity2 = new HttpEntity<>(body, headers);
                restTemplate.postForEntity(url, requestEntity2, String.class);
                log.info("Google Sheets failure alert sent to Telegram channel {}", managementChatId);
            }
        } catch (Exception e) {
            log.error("Failed to send Google Sheets failure alert to Telegram channel " + targetChatId, e);
        }
    }

    private String determineChatId(EventCategory category) {
        if (category == null) return sportsChatId;
        return switch (category) {
            case SPORTS -> sportsChatId;
            case CULTURALS -> culturalChatId;
            case MANAGEMENT -> managementChatId;
            case COMBO -> culturalChatId; // Fallback
        };
    }

    private String formatTelegramMessage(TeamRegistrationDto dto) {
        if (dto.getCategory() == EventCategory.COMBO) {
            StringBuilder sb = new StringBuilder();
            sb.append("🔥 *NEW COMBO PASS REGISTRATION ALERT* 🔥\n");
            sb.append("----------------------------------------\n");
            sb.append("🆔 *Combo Team ID:* `").append(dto.getTeamId() != null ? dto.getTeamId() : "N/A").append("`\n");
            sb.append("👑 *Master Submitter:* ").append(dto.getLeaderName()).append("\n");
            sb.append("📧 *Email Address:* ").append(dto.getLeaderEmail()).append("\n");
            sb.append("📞 *Phone Number:* ").append(dto.getLeaderPhone()).append("\n");
            sb.append("🏛️ *Institution:* ").append(dto.getCollegeName()).append("\n");
            sb.append("🎓 *Year of Study:* ").append(dto.getYearOfStudy()).append("\n");
            sb.append("----------------------------------------\n");
            sb.append("📦 *CONTI-PASS BUNDLE INCLUDES:*\n");

            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<java.util.Map<String, Object>> rosters = mapper.readValue(
                        dto.getComboData(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {}
                );
                for (java.util.Map<String, Object> roster : rosters) {
                    sb.append("• *").append(roster.get("eventName")).append(":* ")
                            .append(roster.get("teamName")).append(" (Leader: ").append(roster.get("leaderName")).append(")\n");
                }
            } catch (Exception e) {
                sb.append("_(Failed to parse combo event details list)_\n");
            }
            sb.append("----------------------------------------\n");
            sb.append("💬 _Verify payment screenshot above._\n");
            return sb.toString();
        }

        return """
                🔥 *NEW REGISTRATION ALERT* 🔥
                ----------------------------------------
                🆔 *Team ID:* `%s`
                🏆 *Team Name:* `%s`
                ✨ *Event Name:* %s
                🗂️ *Category:* `%s`
                👑 *Leader Name:* %s
                📧 *Email Address:* %s
                📞 *Phone Number:* %s
                🏛️ *Institution:* %s
                🎓 *Year of Study:* %s
                👥 *Team Members:* %s
                ----------------------------------------
                💬 _Verify payment screenshot above._
                """.formatted(
                dto.getTeamId() != null ? dto.getTeamId() : "N/A",
                dto.getTeamName() != null && !dto.getTeamName().trim().isEmpty() ? dto.getTeamName() : "Solo",
                dto.getEventName(),
                dto.getCategory() != null ? dto.getCategory().name() : "N/A",
                dto.getLeaderName(),
                dto.getLeaderEmail(),
                dto.getLeaderPhone(),
                dto.getCollegeName(),
                dto.getYearOfStudy(),
                dto.getMemberNames() != null && !dto.getMemberNames().isEmpty() ? dto.getMemberNames() : "Solo"
        );
    }
}
