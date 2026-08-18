package nct.audit.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.audit.domain.AuditLog;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [감사 - MyBatis 매퍼] (F-OPS-015/016)
 * - SQL 본문은 resources/mapper/audit/AuditLogMapper.xml
 */
@Mapper
public interface AuditLogMapper {

    /** 감사로그 행 추가 */
    int insert(AuditLog auditLog);

    /**
     * 조건별 목록 조회 (관리자 화면용, 최신순)
     * - 조건은 전부 선택 사항: null이면 그 조건은 무시된다
     */
    List<AuditLog> selectList(@Param("usrSn") Long usrSn,
                              @Param("audLogTypeCd") String audLogTypeCd,
                              @Param("fromDt") LocalDateTime fromDt,
                              @Param("toDt") LocalDateTime toDt,
                              @Param("limit") int limit);

    /** 담당자 7 연계 · F-OPS-015: 관리자 상세 화면에 마지막 처리 사유를 표시합니다. */
    AuditLog selectLatestByReference(@Param("refTypeCd") String refTypeCd,
                                     @Param("refSn") Long refSn);

    /** 담당자 7 · REQ-OPS-003: 요청 ID로 이전 운영 처리 결과를 조회합니다. */
    AuditLog selectByRequestId(@Param("requestId") String requestId);

    /** 담당자 7 · REQ-OPS-011: 지정 구간의 관리자 로그인 실패 토큰별 횟수입니다. */
    long countAdminLoginFailuresByTokenSince(
            @Param("tokenField") String tokenField,
            @Param("tokenValue") String tokenValue,
            @Param("since") LocalDateTime since);

    /** 담당자 7 · F-OPS-016: 주 대상 또는 연관 대상의 전체 처리 이력을 조회합니다. */
    List<AuditLog> selectHistory(@Param("refTypeCd") String refTypeCd,
                                 @Param("refSn") Long refSn,
                                 @Param("limit") int limit);

    /** 원문 조회 전에 분쟁·거래·채팅방 연결과 전체 메시지 수만 확인합니다. */
    DisputeChatTarget selectDisputeChatTarget(@Param("reportSn") long reportSn);

    /** 검증된 거래 분쟁의 채팅 메시지를 최신 페이지부터 제한 조회합니다. */
    List<ChatMessageView> selectDisputeChatMessages(
            @Param("reportSn") long reportSn,
            @Param("limit") int limit,
            @Param("offset") long offset);
}
