package nct.point.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.point.domain.PointChargeOrder;

/**
 * Claude Code 작성 (BJN, 2026-07-15)
 *
 * [포인트 충전 주문 - MyBatis 매퍼]
 * - SQL 본문은 resources/mapper/point/PointChargeOrderMapper.xml
 */
@Mapper
public interface PointChargeOrderMapper {

    /** 주문 행 추가 (대기 상태로 생성) */
    int insert(PointChargeOrder order);

    /** 주문번호로 행 잠금 후 조회 — 승인 처리 중 같은 주문이 중복 확정되는 것을 막는다 */
    PointChargeOrder selectForUpdateByOrderNo(@Param("ptChgOrdNo") String ptChgOrdNo);

    /** 완료 처리: 상태 + PG결제키 + 연결된 원장SN 기록 */
    int complete(@Param("ptChgOrdSn") long ptChgOrdSn,
                 @Param("statusCd") String statusCd,
                 @Param("pgKey") String pgKey,
                 @Param("ptLdgSn") long ptLdgSn);

    /** 실패/취소 처리: 상태 + PG결제키 + 실패사유 기록 */
    int fail(@Param("ptChgOrdSn") long ptChgOrdSn,
             @Param("statusCd") String statusCd,
             @Param("pgKey") String pgKey,
             @Param("failRsnCn") String failRsnCn);
}
