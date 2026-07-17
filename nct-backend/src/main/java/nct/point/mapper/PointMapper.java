package nct.point.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.point.domain.PointBalance;
import nct.point.domain.PointLedger;

/**
 * [포인트 - MyBatis 매퍼]
 * - SQL 본문은 resources/mapper/point/PointMapper.xml 에 있다 (namespace가 이 인터페이스와 일치)
 */
@Mapper
public interface PointMapper {

    /** 회원 단위 포인트 처리 직렬화를 위한 행 잠금 (USERS는 읽기만, 변경 금지 — 타 도메인 소유) */
    Long lockUser(@Param("usrSn") long usrSn);

    /** 포인트분류별 원장 합계로 잔액 계산 */
    PointBalance selectBalance(@Param("usrSn") long usrSn);

    /** 원장 목록 (공통코드 한글명 조인 포함, 최신순 100건) */
    List<PointLedger> selectLedgerList(@Param("usrSn") long usrSn);

    /** 특정 참조 건의 현재 유효 홀딩 금액 (홀딩분류 원장 합계 — 0보다 크면 홀딩이 살아있음) */
    long selectActiveHoldAmtByRef(@Param("usrSn") long usrSn,
                                  @Param("refTypeCd") String refTypeCd,
                                  @Param("refSn") long refSn);

    /** 원장 행 추가 (원장은 INSERT만 — UPDATE/DELETE 금지) */
    int insertLedger(PointLedger ledger);
}
