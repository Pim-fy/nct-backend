package nct.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 서비스 거래 채팅방 생성의 멱등 결과다. */
@Getter
@AllArgsConstructor
public class ServiceTradeChatRoomCreateResult {

    private final long roomId;
    private final boolean created;
}
