package nct.audit.mapper;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [감사 - 민감정보 제한 조회 결과 모델] (F-OPS-014)
 * - 거래 분쟁·법적 대응 목적으로 제한 조회한 CHAT_MESSAGE 원문 한 건.
 * - 채팅 도메인(담당자4)의 모델을 가져다 쓰지 않고 감사 전용 읽기 모델을 따로 두는 이유:
 *   감사 조회에 필요한 최소 컬럼만 담아 채팅 도메인 코드와의 결합을 만들지 않기 위함
 */
@Data
public class ChatMessageView {

    private Long chMsgSn;
    private Long chRmSn;
    /** 발신자 회원일련번호 */
    private Long usrSn;
    /** 발신자 이름 (USERS 조인) */
    private String usrNm;
    /** 메시지 원문 */
    private String chMsgCn;
    private LocalDateTime chMsgRegDt;
}
