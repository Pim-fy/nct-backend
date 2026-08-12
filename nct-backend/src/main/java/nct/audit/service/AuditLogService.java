package nct.audit.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLog;
import nct.audit.domain.AuditLogType;
import nct.audit.mapper.AuditLogMapper;
import nct.audit.mapper.ChatMessageView;
import nct.audit.mapper.DisputeChatTarget;
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

    private static final int DEFAULT_DISPUTE_CHAT_PAGE_SIZE = 50;
    private static final int MAX_DISPUTE_CHAT_PAGE_SIZE = 100;
    private static final int MAX_DISPUTE_CHAT_PAGE = 1_000_000;

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
        return record(
                actorUsrSn,
                type,
                refType,
                refType == null ? null : refSn,
                reason,
                null,
                null,
                null,
                null,
                null,
                ipAddr);
    }

    /**
     * 담당자 7 · F-OPS-015: 관리자 변경 이력을 구조화해 기록합니다.
     * 주 대상과 연관 대상은 각각 유형·번호가 함께 있거나 함께 없어야 합니다.
     */
    @Transactional
    public long record(
            Long actorUsrSn,
            AuditLogType type,
            RefType refType,
            Long refSn,
            String reason,
            String before,
            String after,
            String requestId,
            RefType relatedRefType,
            Long relatedRefSn,
            String ipAddr) {
        if (type == null
                || (refType == null) != (refSn == null)
                || (relatedRefType == null) != (relatedRefSn == null)
                || (refSn != null && refSn <= 0)
                || (relatedRefSn != null && relatedRefSn <= 0)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        AuditLog log = new AuditLog();
        log.setUsrSn(actorUsrSn);
        log.setAudLogTypeCd(type.getCode());
        log.setAudLogRefTypeCd(refType == null ? null : refType.getCode());
        log.setAudLogRefSn(refSn);
        log.setAudLogRsonCn(reason);
        log.setAudLogBeforeCn(before);
        log.setAudLogAfterCn(after);
        log.setAudLogReqId(requestId);
        log.setAudLogRelRefTypeCd(relatedRefType == null ? null : relatedRefType.getCode());
        log.setAudLogRelRefSn(relatedRefSn);
        log.setAudLogIpAddr(ipAddr);
        auditLogMapper.insert(log);
        return log.getAudLogSn();
    }

    /** 조건별 감사로그 조회 (F-OPS-016, 관리자 화면용) — 조건은 전부 선택 사항, 최신순 */
    public List<AuditLog> search(Long usrSn, String audLogTypeCd,
                                 LocalDateTime fromDt, LocalDateTime toDt, int limit) {
        return auditLogMapper.selectList(usrSn, audLogTypeCd, fromDt, toDt, limit);
    }

    /** 담당자 7 연계 · F-OPS-015: 참조 대상의 최신 관리자 처리 사유를 조회합니다. */
    @Transactional(readOnly = true)
    public AuditLog findLatest(RefType refType, Long refSn) {
        if (refType == null || refSn == null || refSn <= 0) return null;
        return auditLogMapper.selectLatestByReference(refType.getCode(), refSn);
    }

    /** 담당자 7 · F-OPS-016: 주 대상 또는 연관 대상으로 연결된 관리자 이력을 최신순 조회합니다. */
    @Transactional(readOnly = true)
    public List<AuditLog> findHistory(RefType refType, Long refSn, int limit) {
        if (refType == null || refSn == null || refSn <= 0 || limit < 1 || limit > 200) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return auditLogMapper.selectHistory(refType.getCode(), refSn, limit);
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

        if (reason.trim().length() > 400) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "민감정보 제한 조회 사유는 400자 이하여야 합니다.");
        }

        if (chMsgSn <= 0 || trdDspSn <= 0
                || auditLogMapper.countDisputeChatMessageLink(chMsgSn, trdDspSn) == 0) {
            throw new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND,
                    "해당 거래 분쟁에 연결된 채팅 메시지를 찾을 수 없습니다.");
        }

        // 원문조회 감사로그 — 참조는 근거가 된 거래 분쟁 건, 사유에 조회 대상 메시지를 함께 기록
        record(adminUsrSn, AuditLogType.SENSITIVE_VIEW, RefType.TRADE_DISPUTE, trdDspSn,
                String.format("채팅 메시지 %d번 원문 조회 — 사유: %s", chMsgSn, reason.trim()), ipAddr);

        ChatMessageView message = auditLogMapper.selectChatMessageView(chMsgSn);
        if (message == null) {
            throw new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND);
        }
        return message;
    }

    /**
     * 담당자 7 연계 · F-OPS-005/014: 분쟁에 실제로 연결된 채팅만 사유·감사기록 후 제한 조회합니다.
     * 메시지 원문은 감사 INSERT가 성공한 뒤에만 읽으며, 페이지 크기를 제한해 무제한 조회를 막습니다.
     */
    @Transactional
    public DisputeChatViewResult viewDisputeChatMessages(
            long adminUsrSn,
            long trdDspSn,
            String reason,
            String ipAddr,
            Integer requestedPage,
            Integer requestedSize) {
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new CustomException(ErrorCode.MISSING_REQUIRED_FIELD,
                    "채팅 내역 열람 사유를 입력해야 합니다.");
        }
        if (normalizedReason.length() > 400) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "채팅 내역 열람 사유는 400자 이하여야 합니다.");
        }
        if (trdDspSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래 분쟁 번호가 올바르지 않습니다.");
        }

        int page = requestedPage == null ? 1 : requestedPage;
        int size = requestedSize == null ? DEFAULT_DISPUTE_CHAT_PAGE_SIZE : requestedSize;
        if (page < 1 || page > MAX_DISPUTE_CHAT_PAGE
                || size < 1 || size > MAX_DISPUTE_CHAT_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "채팅 내역 페이지 조건이 올바르지 않습니다.");
        }

        DisputeChatTarget target = auditLogMapper.selectDisputeChatTarget(trdDspSn);
        if (target == null) {
            throw new CustomException(ErrorCode.NOT_FOUND,
                    "존재하지 않는 거래 분쟁입니다.");
        }

        long totalItems = target.getMessageCount();
        int totalPages = totalItems == 0
                ? 0
                : (int) Math.min(Integer.MAX_VALUE, (totalItems + size - 1) / size);
        if (page > Math.max(totalPages, 1)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "존재하지 않는 채팅 내역 페이지입니다.");
        }

        record(adminUsrSn, AuditLogType.SENSITIVE_VIEW, RefType.TRADE_DISPUTE, trdDspSn,
                String.format("분쟁 채팅 내역 원문 조회 (페이지 %d) — 사유: %s",
                        page, normalizedReason),
                ipAddr);

        List<ChatMessageView> messages = new ArrayList<>();
        if (target.getRoomSn() != null && target.getMessageCount() > 0) {
            long offset = (long) (page - 1) * size;
            messages.addAll(auditLogMapper.selectDisputeChatMessages(trdDspSn, size, offset));
            Collections.reverse(messages);
        }

        return new DisputeChatViewResult(
                target.getDisputeSn(),
                target.getTradeSn(),
                target.getRoomSn() != null,
                List.copyOf(messages),
                page,
                size,
                totalItems,
                totalPages);
    }
}
