package nct.chat.dto;

import lombok.Data;

/** 거래 당사자 검증을 통과한 채팅방의 내부 조회 결과다. */
@Data
public class ChatRoomAccess {

    private Long roomId;
    private Long tradeId;
    private String roomStatus;
    // 채팅방 상태가 이전 데이터로 ACTIVE여도 완료 거래의 전송을 막기 위한 원본 거래 상태다.
    private String tradeStatus;
}
