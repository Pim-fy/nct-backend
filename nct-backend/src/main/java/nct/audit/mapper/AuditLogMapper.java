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

    /**
     * 민감정보 제한 조회 대상 채팅 메시지 원문 (F-OPS-014)
     * - CHAT_MESSAGE는 담당자4 소유 테이블 — 여기서는 읽기 전용 SELECT만 하며 절대 변경하지 않는다
     */
    ChatMessageView selectChatMessageView(@Param("chMsgSn") long chMsgSn);

    /** 전달된 메시지가 해당 거래 분쟁의 거래 채팅방에 실제로 속하는지 확인합니다. */
    int countDisputeChatMessageLink(
            @Param("chMsgSn") long chMsgSn,
            @Param("trdDspSn") long trdDspSn);

    /** 원문 조회 전에 분쟁·거래·채팅방 연결과 전체 메시지 수만 확인합니다. */
    DisputeChatTarget selectDisputeChatTarget(@Param("trdDspSn") long trdDspSn);

    /** 검증된 거래 분쟁의 채팅 메시지를 최신 페이지부터 제한 조회합니다. */
    List<ChatMessageView> selectDisputeChatMessages(
            @Param("trdDspSn") long trdDspSn,
            @Param("limit") int limit,
            @Param("offset") long offset);
}
