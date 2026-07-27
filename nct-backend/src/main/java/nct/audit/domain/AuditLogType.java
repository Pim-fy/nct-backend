package nct.audit.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [감사 - 감사 행위 유형 코드] (F-OPS-015)
 * - AUDIT_LOG.AUD_LOG_TYPE_CD에 저장되는 공통코드(AUDG01)
 * - RefType과 같은 방식: DB에는 문자열 코드(AUDC0001~)로 저장하고,
 *   자바에서는 이 enum으로만 다뤄서 오타 코드가 저장되는 것을 컴파일 시점에 차단한다
 */
@Getter
@RequiredArgsConstructor
public enum AuditLogType {

    /** 생성 */
    CREATE("AUDC0001"),
    /** 수정 */
    UPDATE("AUDC0002"),
    /** 삭제 */
    DELETE("AUDC0003"),
    /** 원문조회 — 민감정보 제한 조회(F-OPS-014) 전용 */
    SENSITIVE_VIEW("AUDC0004"),
    /** 관리자승인 */
    ADMIN_APPROVE("AUDC0005"),
    /** 관리자반려 */
    ADMIN_REJECT("AUDC0006"),
    /** 상태변경 */
    STATUS_CHANGE("AUDC0007");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
