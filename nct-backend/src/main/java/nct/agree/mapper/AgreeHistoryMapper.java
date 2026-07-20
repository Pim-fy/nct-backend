package nct.agree.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.agree.domain.AgreeHistory;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [동의 - MyBatis 매퍼] (F-OPS-017)
 * - SQL 본문은 resources/mapper/agree/AgreeHistoryMapper.xml
 */
@Mapper
public interface AgreeHistoryMapper {

    /** 동의 이력 행 추가 */
    int insert(AgreeHistory history);

    /** 회원별 동의 이력 (최신순 100건) — 분쟁 대응 시 "이 회원이 언제 어디에 동의했나" 추적용 */
    List<AgreeHistory> selectListByUser(@Param("usrSn") long usrSn);
}
