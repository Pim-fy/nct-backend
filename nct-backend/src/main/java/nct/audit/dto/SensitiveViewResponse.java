package nct.audit.dto;

import java.time.format.DateTimeFormatter;

import lombok.Builder;
import lombok.Getter;
import nct.audit.mapper.ChatMessageView;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [감사 - 민감정보 제한 조회 응답 DTO] (F-OPS-014)
 * - 감사로그 기록이 끝난 뒤에만 만들어지는 응답 — 원문(content)이 여기 실려 나간다
 */
@Getter
@Builder
public class SensitiveViewResponse {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Long chMsgSn;
    private final Long chRmSn;

    /** 발신자 (이름 + 회원번호) */
    private final String senderName;
    private final Long senderSn;

    /** 메시지 원문 */
    private final String content;
    private final String sentAt;

    /** 도메인 모델 → 응답 DTO 변환 */
    public static SensitiveViewResponse from(ChatMessageView m) {
        return SensitiveViewResponse.builder()
                .chMsgSn(m.getChMsgSn())
                .chRmSn(m.getChRmSn())
                .senderName(m.getUsrNm())
                .senderSn(m.getUsrSn())
                .content(m.getChMsgCn())
                .sentAt(m.getChMsgRegDt() != null ? m.getChMsgRegDt().format(DATE_FMT) : null)
                .build();
    }
}
