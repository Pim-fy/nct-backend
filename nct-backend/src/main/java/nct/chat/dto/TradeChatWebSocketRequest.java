package nct.chat.dto;

import lombok.Data;

/** 브라우저가 직거래 채팅 WebSocket으로 보내는 구독·메시지 전송 요청이다. */
@Data
public class TradeChatWebSocketRequest {

    private String type;
    private Long roomId;
    private String content;
    private String detectionKey;
}
