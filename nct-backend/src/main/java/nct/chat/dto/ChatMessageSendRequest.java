package nct.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 채팅방 참여자가 메시지를 전송할 때 사용하는 요청이다. */
@Data
public class ChatMessageSendRequest {

    @NotBlank(message = "메시지를 입력해 주세요.")
    @Size(max = 500, message = "메시지는 500자 이내로 입력해 주세요.")
    private String content;

    @NotBlank(message = "메시지 요청 식별값이 필요합니다.")
    private String detectionKey;
}
