package nct.chat.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 거래 당사자 검증을 통과한 채팅방의 내부 조회 결과다. */
@Data
public class ChatRoomAccess {

    private Long roomId;
    private Long tradeId;
    private Long counterpartUserId;
    private String roomStatus;
    // 채팅방 상태가 이전 데이터로 ACTIVE여도 종료 거래의 전송 가능 여부를 판단하기 위한 원본 거래 상태다.
    private String tradeStatus;
    // 완료 상태 이력의 최신 시각이다. 거래 완료 뒤 48시간 동안만 채팅을 계속 허용한다.
    private LocalDateTime completedAt;
}
