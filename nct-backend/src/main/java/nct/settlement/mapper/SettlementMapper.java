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

    /** 거래번호 기준 단건 조회 */
    Settlement selectByTrade(@Param("trdSn") long trdSn);

    /** 거래별 정산 생성 경합 처리와 일치 검증을 위한 잠금 조회 */
    Settlement selectByTradeForUpdate(@Param("trdSn") long trdSn);

    /** 상태코드 갱신 (requireStatus 검증 통과 후에만 호출) */
    int updateStatus(
            @Param("stlmSn") long stlmSn,
            @Param("statusCd") String statusCd,
            @Param("actorId") String actorId);

    /** 회원별 정산 목록 (최신순 100건) */
    List<Settlement> selectListByUser(@Param("usrSn") long usrSn);
}
