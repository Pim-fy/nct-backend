package nct.audit.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLog;
import nct.audit.domain.AuditLogType;
import nct.audit.mapper.AuditLogMapper;
import nct.audit.mapper.ChatMessageView;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [감사 - 서비스 계약] (담당자6 백종남, F-OPS-015/016)
 *
 * 포인트·입찰·정산·관리자 조치·민감정보 접근을 기록하는 공용 계약이다.
 * 다른 도메인 담당자는 AUDIT_LOG 테이블을 직접 INSERT하지 않고 record()를 호출한다
 * (업무분장 v10 섹션8 — 감사 계약은 담당자6 제공, 전 담당자 소비).
 *
 * 트랜잭션 방침: 별도 트랜잭션(REQUIRES_NEW)을 쓰지 않고 호출자 트랜잭션에 합류한다.
 * 행위가 롤백되면 "일어나지 않은 행위"의 감사로그도 함께 사라지는 것이 기록 정합에 맞기 때문.
 * 감사로그는 3년 보존 대상이라 삭제 기능을 제공하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /**
     * 감사로그 기록 (F-OPS-015) — 전 담당자 공용 계약
     *
     * @param actorUsrSn 행위자 회원일련번호 (시스템 자동 처리면 null)
     * @param type       감사 행위 유형 (AuditLogType — 승인/반려/상태변경/원문조회 등)
     * @param refType    무엇에 대한 행위인지 참조유형 (없으면 null)
     * @param refSn      참조 건 일련번호 (refType 없으면 null)
     * @param reason     사유·설명 (화면에 그대로 노출되므로 사람이 읽을 수 있는 문장으로)
     * @param ipAddr     행위자 IP (요청 컨텍스트가 없으면 null)
     * @return 생성된 감사로그 일련번호
     */
    @Transactional
    public long record(Long actorUsrSn, AuditLogType type, RefType refType, Long refSn,
                       String reason, String ipAddr) {
        AuditLog log = new AuditLog();
        log.setUsrSn(actorUsrSn);
        log.setAudLogTypeCd(type.getCode());
        log.setAudLogRefTypeCd(refType == null ? null : refType.getCode());
        log.setAudLogRefSn(refType == null ? null : refSn);
        log.setAudLogRsonCn(reason);
        log.setAudLogIpAddr(ipAddr);
        auditLogMapper.insert(log);
        return log.getAudLogSn();
    }

    /** 조건별 감사로그 조회 (F-OPS-016, 관리자 화면용) — 조건은 전부 선택 사항, 최신순 */
    public List<AuditLog> search(Long usrSn, String audLogTypeCd,
                                 LocalDateTime fromDt, LocalDateTime toDt, int limit) {
        return auditLogMapper.selectList(usrSn, audLogTypeCd, fromDt, toDt, limit);
    }

    /**
     * 민감정보(채팅 메시지) 원문 제한 조회 (F-OPS-014)
     *
     * 정본 규칙: 거래 분쟁·법적 대응 건에 한해, 사유 입력 + 감사로그 기록 후에만 원문을 반환한다.
     * 순서가 중요하다 — 감사로그 INSERT가 먼저이고 원문 반환이 나중이라, 로그 없이 원문만
     * 새어 나가는 경로가 코드상 존재하지 않는다 (같은 트랜잭션이므로 로그 실패 시 조회도 실패).
     *
     * @param adminUsrSn 조회하는 관리자
     * @param chMsgSn    조회 대상 채팅 메시지
     * @param trdDspSn   연결된 거래 분쟁 건 일련번호 (필수 — 분쟁 없는 임의 열람 차단)
     * @param reason     조회 사유 (필수)
     * @param ipAddr     관리자 IP
     */
    @Transactional
    public ChatMessageView viewChatMessage(long adminUsrSn, long chMsgSn, long trdDspSn,
                                           String reason, String ipAddr) {
        // 사유 없는 조회 요청은 실패한다 (F-OPS-014 예외 규칙).
        // 컨트롤러 @Valid와 별개로 서비스에서도 지키는 이유: 이 계약을 다른 코드가 직접 호출해도 뚫리지 않게
        if (reason == null || reason.isBlank()) {
            throw new CustomException(ErrorCode.MISSING_REQUIRED_FIELD, "민감정보 제한 조회 사유를 입력해야 합니다.");
        }

        ChatMessageView message = auditLogMapper.selectChatMessageView(chMsgSn);
        if (message == null) {
            throw new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND,
                    "존재하지 않는 채팅 메시지입니다: " + chMsgSn);
        }

        // 원문조회 감사로그 — 참조는 근거가 된 거래 분쟁 건, 사유에 조회 대상 메시지를 함께 기록
        record(adminUsrSn, AuditLogType.SENSITIVE_VIEW, RefType.TRADE_DISPUTE, trdDspSn,
                String.format("채팅 메시지 %d번 원문 조회 — 사유: %s", chMsgSn, reason), ipAddr);
        return message;
    }
}
