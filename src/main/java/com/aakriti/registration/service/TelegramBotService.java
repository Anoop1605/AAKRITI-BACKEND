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

    @Async
    public void sendRegistrationAlert(TeamRegistrationDto dto, byte[] screenshotBytes, String originalFilename) {
        if (dto.getCategory() == EventCategory.COMBO) {
            log.info("Sending COMBO PASS registration alert to both Cultural and Management groups.");
            sendToChannel(dto, screenshotBytes, originalFilename, culturalChatId);
            sendToChannel(dto, screenshotBytes, originalFilename, managementChatId);
        } else {
            String targetChatId = determineChatId(dto.getCategory());
            sendToChannel(dto, screenshotBytes, originalFilename, targetChatId);
        }
    }

    private void sendToChannel(TeamRegistrationDto dto, byte[] screenshotBytes, String originalFilename, String targetChatId) {
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

        } catch (Exception e) {
            log.error("Failed to send notification message to Telegram channel " + targetChatId, e);
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
