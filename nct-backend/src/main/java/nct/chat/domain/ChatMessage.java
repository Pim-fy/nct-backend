package nct.chat.domain;

import lombok.Data;

/** CHAT_MESSAGE에 저장할 발신 메시지다. */
@Data
public class ChatMessage {

    private Long messageId;
    private long roomId;
    private long senderUserId;
    private String content;
}
