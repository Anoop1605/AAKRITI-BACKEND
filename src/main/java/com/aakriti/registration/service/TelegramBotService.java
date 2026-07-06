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
        // 1. Resolve which channel to target based on the enum/string category
        String targetChatId = determineChatId(dto.getCategory());
        
        // 2. Format a highly readable, markdown-searchable caption for the image
        String caption = formatTelegramMessage(dto);

        // 3. Build the endpoint URL for Telegram's sendPhoto API
        String url = "https://api.telegram.org/bot" + botToken + "/sendPhoto";

        try {
            // 4. Wrap byte[] into a ByteArrayResource so RestTemplate can parse it as binary
            ByteArrayResource fileResource = new ByteArrayResource(screenshotBytes) {
                @Override
                public String getFilename() {
                    return originalFilename != null ? originalFilename : "receipt.jpg";
                }
            };

            // 5. Prepare Multipart Request Payloads
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", targetChatId);
            body.add("photo", fileResource);
            body.add("caption", caption);
            body.add("parse_mode", "Markdown");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 6. Fire request to Telegram
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            log.info("Telegram notification sent successfully. Status code: {}", response.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to send notification message to Telegram channel", e);
            // Non-blocking fallback so user registration isn't rolled back completely if Telegram undergoes brief lag
        }
    }

    private String determineChatId(EventCategory category) {
        if (category == null) return sportsChatId; // Fallback default
        return switch (category) {
            case SPORTS -> sportsChatId;
            case CULTURALS -> culturalChatId;
            case MANAGEMENT -> managementChatId;
        };
    }

    private String formatTelegramMessage(TeamRegistrationDto dto) {
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
