package nct.chat.domain;

import lombok.Data;

/** 직거래 거래와 1:1로 연결되는 CHAT_ROOM 생성용 모델이다. */
@Data
public class ChatRoom {

    private Long roomId;
    private long tradeId;
    private String roomStatus;
}
