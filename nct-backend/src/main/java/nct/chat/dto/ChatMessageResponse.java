package nct.chat.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 현재 로그인 사용자를 기준으로 발신 구분을 포함한 채팅 메시지 응답이다. */
@Data
public class ChatMessageResponse {

    private Long messageId;
    private String senderType;
    private String content;
    private LocalDateTime sentAt;
    private Boolean read;
}
