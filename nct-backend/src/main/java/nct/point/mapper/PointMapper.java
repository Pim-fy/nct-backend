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

    /**
     * 진행 중인 거래 문제 건수 (F-PAY-010 전환 차단 조건 — "분쟁 없음 확인 후 전환")
     * - 회원이 당사자(판매자/구매자)인 거래에 걸린 '접수'·'처리중' 상태의 거래 문제를 센다
     * - TRADE·TRADE_DISPUTE는 타 담당자 소유 — 읽기 전용 조회만, 변경 금지
     */
    int countActiveDisputes(@Param("usrSn") long usrSn);

    /**
     * 특정 회원·참조 건의 현재 유효 보관금 (보관금전환 ESCROW − 와 환불 REFUND + 유형 합산)
     * - 음수면 보관금이 살아있음(빠져나간 상태), 0이면 없음 또는 이미 환불됨
     * - debitEscrow 중복 검사·refundEscrow 이중 환불 차단에 쓴다 (F-SVC-013, 분쟁 환불)
     */
    long selectActiveEscrowAmtByMemberRef(@Param("usrSn") long usrSn,
                                          @Param("refTypeCd") String refTypeCd,
                                          @Param("refSn") long refSn);

    /**
     * 특정 참조 건의 현재 유효 보관금 — 회원 무관 버전.
     * 정산 전환(creditEscrowToSettleable)은 제공자 쪽에서 호출되어 지불자 회원번호를 모르므로
     * 참조 건만으로 보관금을 찾는다 (한 거래의 보관금 지불자는 한 명뿐이라 합산이 곧 그 사람 것)
     */
    long selectActiveEscrowAmtByRef(@Param("refTypeCd") String refTypeCd,
                                    @Param("refSn") long refSn);

    /** 특정 참조 건으로 이미 정산 지급(SETTLE +)된 금액 — 0보다 크면 이중 정산·정산 후 환불을 거부한다 */
    long selectSettledAmtByRef(@Param("refTypeCd") String refTypeCd,
                               @Param("refSn") long refSn);

    /**
     * 특정 거래의 진행 중 거래 문제 건수 (F-SVC-015 정산 전환 차단 조건 — 거래 단위 버전)
     * - countActiveDisputes(회원 단위)와 달리 해당 거래 건에 걸린 분쟁만 본다
     * - TRADE_DISPUTE는 타 담당자 소유 — 읽기 전용 조회만, 변경 금지
     */
    int countActiveDisputesByTrade(@Param("trdSn") long trdSn);

    /**
     * 대사 배치(F-PT-06/QSC-PT-08) 전용 — 전 회원 원장 전체 합계.
     * selectBalance는 회원 1명 기준이라(WHERE USR_SN=...) 재사용 불가, 회원 무관 전체 집계다.
     */
    long selectTotalLedgerSum();

    /**
     * 대사 배치 전용 — 시스템 경계를 넘나드는 원장 유형(충전 PTLC0004, 환전출금 PTLC0010,
     * 환전복원 PTLC0011)과, 그 경계이동을 취소하는 보정(PTLC0009 ADJUST — D-027 충전 내부실패
     * 회수 전용, reverseCharge)까지의 합계. ADJUST를 빼면 D-027 보상이 실행될 때마다
     * "충전은 +amt로 잡혔는데 그 취소분은 안 잡혀서" 항등식이 거짓으로 깨진다 — 실제로
     * 2026-07-21 이 누락 때문에 공유 DB에서 위양성(false positive) 위험 이벤트가 발생했다.
     * 나머지 유형(홀딩·반환·보관금전환·정산·전환·환불)은 전부 회원 내부 카테고리 이동이라
     * 정상이라면 거래별로 합이 0이 된다 — selectTotalLedgerSum과 항등식을 이룬다.
     */
    long selectBoundaryCrossingSum();
}
