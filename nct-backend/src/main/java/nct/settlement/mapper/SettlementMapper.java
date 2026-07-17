package nct.settlement.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.settlement.domain.Settlement;

/**
 * [정산 - MyBatis 매퍼]
 * - SQL 본문은 resources/mapper/settlement/SettlementMapper.xml
 */
@Mapper
public interface SettlementMapper {

    /** 정산 행 추가 (대기 상태로 생성) */
    int insert(Settlement settlement);

    /** 상태 전이 검증을 위해 행 잠금 후 조회 — 동시 상태 변경 경합 방지 */
    Settlement selectForUpdate(@Param("stlmSn") long stlmSn);

    /** 상태코드 갱신 (requireStatus 검증 통과 후에만 호출) */
    int updateStatus(@Param("stlmSn") long stlmSn, @Param("statusCd") String statusCd);

    /** 회원별 정산 목록 (최신순 100건) */
    List<Settlement> selectListByUser(@Param("usrSn") long usrSn);

    /** 거래별 정산 목록 */
    List<Settlement> selectListByTrade(@Param("trdSn") long trdSn);
}
