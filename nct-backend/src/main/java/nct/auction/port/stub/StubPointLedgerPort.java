package nct.auction.port.stub;

import org.springframework.stereotype.Component;

import nct.auction.port.PointLedgerPort;

import lombok.extern.slf4j.Slf4j;

/**
 * ================================================================================
 *  [임시 구현체 - TODO: 담당자6 실제 구현체로 교체 필요]
 * ================================================================================
 * - POINT_LEDGER 테이블의 고정 기술 소유자는 담당자6 이다. 이 클래스는 그 실제 구현체가
 *   아직 만들어지지 않아 애플리케이션이 아예 기동조차 안 되는 상황(빈 부재)을 막기 위한
 *   "최소 동작 대역"일 뿐, POINT_LEDGER 원장을 실제로 조회하지 않는다.
 * - 항상 true(잔액 충분)를 반환하므로, 이 스텁이 붙어 있는 동안에는 F-AUC-013의
 *   "6. 보유 포인트 검증" 규칙이 사실상 검증되지 않는 상태라는 점에 유의해야 한다.
 * - 담당자6이 실제 PointLedgerPort 구현체(POINT_LEDGER 원장 조회)를 만들어 Bean 으로
 *   등록하면, Spring 컨테이너에 같은 타입의 Bean 이 2개가 되어 기동 시 충돌이 난다.
 *   그때 이 클래스는 반드시 삭제해야 한다 (또는 이 파일 자체를 지운다).
 * ================================================================================
 */
@Slf4j
@Component
public class StubPointLedgerPort implements PointLedgerPort {

    @Override
    public boolean hasAvailableBalance(Long usrSn, Long amount) {
        log.warn("[STUB] StubPointLedgerPort.hasAvailableBalance() 호출됨 (usrSn={}, amount={}) - " +
                 "항상 true 를 반환합니다. 담당자6의 실제 POINT_LEDGER 조회 구현체로 반드시 교체해야 합니다.",
                 usrSn, amount);
        return true;
    }

    @Override
    public void holdForBid(Long usrSn, Long amount, Long bidSn) {
        // 실제 원장 반영 없음 - 아무 일도 일어나지 않은 것처럼 조용히 통과시킨다.
        log.warn("[STUB] StubPointLedgerPort.holdForBid() 호출됨 (usrSn={}, amount={}, bidSn={}) - " +
                 "아무 것도 저장하지 않습니다. 담당자6의 실제 홀딩 구현체로 반드시 교체해야 합니다.",
                 usrSn, amount, bidSn);
    }

    @Override
    public void releaseHold(Long usrSn, Long amount, Long bidSn) {
        log.warn("[STUB] StubPointLedgerPort.releaseHold() 호출됨 (usrSn={}, amount={}, bidSn={}) - " +
                 "아무 것도 반환하지 않습니다. 담당자6의 실제 반환 구현체로 반드시 교체해야 합니다.",
                 usrSn, amount, bidSn);
    }
}
