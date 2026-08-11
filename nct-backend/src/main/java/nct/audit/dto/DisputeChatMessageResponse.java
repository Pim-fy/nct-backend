package nct.audit.dto;

import java.time.format.DateTimeFormatter;

import lombok.Builder;
import lombok.Getter;
import nct.audit.mapper.ChatMessageView;
import nct.member.dto.AdminMemberIdentityResponse;

/** 담당자 7 · F-OPS-014: 내부 회원번호 없이 관리자에게 제공하는 채팅 메시지입니다. */
@Getter
@Builder
public class DisputeChatMessageResponse {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Long messageSn;
    private final String senderLoginId;
    private final String senderNickname;
    private final String content;
    private final String sentAt;

    public static DisputeChatMessageResponse from(
            ChatMessageView message,
            AdminMemberIdentityResponse identity) {
        String loginId = identity == null ? null : identity.getLoginId();
        if (loginId != null && loginId.startsWith("OAUTH_")) {
            loginId = null;
        }

        String nickname = identity == null ? null : identity.getNickname();
        if (nickname == null || nickname.isBlank()) {
            nickname = message.getUsrNm();
        }
        return DisputeChatMessageResponse.builder()
                .messageSn(message.getChMsgSn())
                .senderLoginId(loginId)
                .senderNickname(nickname)
                .content(message.getChMsgCn())
                .sentAt(message.getChMsgRegDt() == null
                        ? null
                        : message.getChMsgRegDt().format(DATE_FMT))
                .build();
    }
}
