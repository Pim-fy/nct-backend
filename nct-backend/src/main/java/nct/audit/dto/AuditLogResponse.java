package nct.audit.dto;

import java.time.format.DateTimeFormatter;

import lombok.Builder;
import lombok.Getter;
import nct.audit.domain.AuditLog;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [감사 - 감사로그 행 응답 DTO] (F-OPS-016)
 * - GET /api/admin/audit/logs 응답 본문의 배열 원소
 * - 코드값 대신 한글명(행위 유형·참조유형·행위자 이름)을 담아 화면이 그대로 표시하게 한다
 */
@Getter
@Builder
public class AuditLogResponse {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Long id;
    private final String date;

    /** 행위자 (이름 + 회원번호, 시스템 자동 기록이면 둘 다 null) */
    private final String userName;
    private final Long userSn;

    /** 행위 유형 한글명 (생성/수정/원문조회/관리자승인 등) */
    private final String type;
    private final String typeCd;

    /** 참조 대상 (한글명 + 일련번호, 없으면 null) */
    private final String refType;
    private final Long refSn;

    private final String reason;
    private final String ipAddr;

    /** 도메인 모델 → 응답 DTO 변환 */
    public static AuditLogResponse from(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getAudLogSn())
                .date(log.getAudLogRegDt() != null ? log.getAudLogRegDt().format(DATE_FMT) : null)
                .userName(log.getUsrNm())
                .userSn(log.getUsrSn())
                .type(log.getAudLogTypeNm())
                .typeCd(log.getAudLogTypeCd())
                .refType(log.getRefTypeNm())
                .refSn(log.getAudLogRefSn())
                .reason(log.getAudLogRsonCn())
                .ipAddr(log.getAudLogIpAddr())
                .build();
    }
}
