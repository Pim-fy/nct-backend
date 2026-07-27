package nct.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 거래 생성 흐름이 재시도돼도 같은 채팅방을 식별할 수 있도록 반환하는 멱등 생성 결과다. */
@Getter
@AllArgsConstructor
public class OfflineTradeChatRoomCreateResult {

    private final long roomId;
    private final boolean created;
}
