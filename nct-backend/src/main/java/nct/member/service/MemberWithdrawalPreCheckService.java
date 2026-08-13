package nct.member.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import nct.abuse.mapper.AbuseReportMapper;
import nct.auction.mapper.AuctionMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.point.domain.PointBalance;
import nct.point.service.PointService;
import nct.trade.mapper.TradeMapper;

// @ai_generated
/**
 * F-AUTH-011/POL-AUTH-013: 회원 탈퇴 전 하드 차단 조건을 검증한다.
 * 정지 계정은 거래 관련 조건(진행 중 거래)을 검증하지 않는다 - 정지 처리(POL-AUTH-006) 시
 * 진행 중 거래가 이미 관리자 조치로 보류(TRDC0007)되고, 이 보류를 재개시키는 정책이 별도로
 * 없어 정지 계정 본인이 로그인해 직접 해소할 수도 없기 때문이다(ISS-027).
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalPreCheckService {

    private final PointService pointService;
    private final TradeMapper tradeMapper;
    private final AuctionMapper auctionMapper;
    private final AbuseReportMapper abuseReportMapper;

    /** 하나라도 걸리면 사유를 모아 WITHDRAWAL_BLOCKED로 즉시 중단한다. */
    public void check(Long usrSn, boolean skipTradeCheck) {
        List<String> reasons = new ArrayList<>();

        PointBalance balance = pointService.getBalance(usrSn);
        long totalAmt = balance.getTotalAmt();
        if (totalAmt > 0) {
            // @ai_generated: (QA P2-2) getTotalAmt()는 사용가능+홀딩+정산가능 3종 합계인데
            // "사용 가능한 포인트"라고만 하면 사용자가 사용가능 잔액만 0으로 만들면 되는 줄 오해할
            // 수 있어, 세 버킷을 모두 소진해야 한다는 걸 문구에 명시한다.
            reasons.add(String.format(
                    "보유 포인트가 %,d P 남아있습니다(사용가능·홀딩·정산가능 합계).", totalAmt));
        }

        if (!skipTradeCheck) {
            int tradeCount = tradeMapper.countBlockingTradesByUser(usrSn);
            if (tradeCount > 0) {
                reasons.add(String.format("진행 중인 거래가 %d건 있습니다.", tradeCount));
            }
        }

        int auctionCount = auctionMapper.countActiveSellerAuctions(usrSn);
        if (auctionCount > 0) {
            reasons.add(String.format("판매 중인 경매가 %d건 있습니다.", auctionCount));
        }

        int reportCount = abuseReportMapper.countOpenReportsByUser(usrSn);
        if (reportCount > 0) {
            reasons.add(String.format("진행 중인 신고가 %d건 있습니다.", reportCount));
        }

        if (!reasons.isEmpty()) {
            throw new CustomException(ErrorCode.WITHDRAWAL_BLOCKED, String.join(" ", reasons));
        }
    }
}
