package nct.chat.dto;

import lombok.Data;

/** 거래 당사자 검증을 통과한 채팅방의 내부 조회 결과다. */
@Data
public class ChatRoomAccess {

    private Long roomId;
    private Long tradeId;
    private String roomStatus;
}
