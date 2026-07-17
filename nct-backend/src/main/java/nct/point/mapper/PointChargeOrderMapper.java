package nct.point.mapper;

import java.util.List;

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

    /** 내 충전 주문 목록 (최신순 100건) — 실패·취소·대기 건까지 전부 포함해 시도 이력을 보여준다 */
    List<PointChargeOrder> selectListByUser(@Param("usrSn") long usrSn);
}
