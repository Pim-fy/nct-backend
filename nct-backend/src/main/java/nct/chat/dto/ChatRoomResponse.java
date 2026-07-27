package nct.chat.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 채팅방 목록과 대화 헤더에 필요한 요약 정보다. */
@Data
public class ChatRoomResponse {

    private Long roomId;
    private Long tradeId;
    private String counterpartNickname;
    private String productName;
    private String roomStatus;
    private String lastMessage;
    private LocalDateTime latestMessageAt;
    private int unreadCount;
}
