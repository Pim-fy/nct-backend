package nct.auction.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nct.auction.port.PointLedgerPort;

/**
 * [담당자6(포인트 원장) 계약의 Fake 구현체]
 * - 회원번호별 "사용 가능 포인트"를 메모리에 들고 있다가, 입찰 금액과 비교만 해준다.
 * - holdForBid/releaseHold 는 F-AUC-014 구현을 위해 새로 추가된 계약이다.
 *   실제 원장 증감까지 흉내내지는 않고, "누가 호출됐는지"를 기록만 해서
 *   테스트에서 "이 시나리오에서 정확히 이 사람의 홀딩이 반환되었는가"를 검증할 수 있게 한다.
 */
public class FakePointLedgerPort implements PointLedgerPort {

    private final Map<Long, Long> availableBalances = new HashMap<>();

    /** 테스트에서 "홀딩이 몇 번, 누구에게, 얼마 호출됐는지" 확인하기 위한 호출 기록 */
    public final List<HoldCall> holdCalls = new ArrayList<>();
    public final List<HoldCall> releaseCalls = new ArrayList<>();

    public record HoldCall(Long usrSn, Long amount, Long bidSn) {
    }

    /** 테스트 준비 단계(given)에서 "이 사용자는 이만큼의 포인트를 가진다"를 등록한다. */
    public void setBalance(Long usrSn, long amount) {
        availableBalances.put(usrSn, amount);
    }

    @Override
    public boolean hasAvailableBalance(Long usrSn, Long amount) {
        long balance = availableBalances.getOrDefault(usrSn, 0L);
        return balance >= amount;
    }

    @Override
    public void holdForBid(Long usrSn, Long amount, Long bidSn) {
        holdCalls.add(new HoldCall(usrSn, amount, bidSn));
    }

    @Override
    public void releaseHold(Long usrSn, Long amount, Long bidSn) {
        releaseCalls.add(new HoldCall(usrSn, amount, bidSn));
    }
}
