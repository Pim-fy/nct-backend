package nct.chat.dto;

import lombok.Builder;
import lombok.Getter;

/** 서버가 직거래 채팅 WebSocket 구독자에게 보내는 이벤트다. */
@Getter
@Builder
public class TradeChatWebSocketEvent {

    private final String type;
    private final Long roomId;
    private final ChatMessageResponse chatMessage;
    private final String message;
}
