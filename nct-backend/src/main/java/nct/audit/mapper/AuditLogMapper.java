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

    /**
     * 민감정보 제한 조회 대상 채팅 메시지 원문 (F-OPS-014)
     * - CHAT_MESSAGE는 담당자4 소유 테이블 — 여기서는 읽기 전용 SELECT만 하며 절대 변경하지 않는다
     */
    ChatMessageView selectChatMessageView(@Param("chMsgSn") long chMsgSn);
}
