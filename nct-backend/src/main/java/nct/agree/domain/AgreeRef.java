package nct.agree.domain;

import lombok.Getter;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [동의 - 동의 대상 참조] (F-OPS-017)
 * - AGREE_HISTORY는 참조 컬럼 5개(입찰·경매·포인트원장·거래문제·거래) 중 정확히 1개만
 *   채워져야 한다(DB CHECK 제약). 호출자가 컬럼을 잘못 조합하는 실수를 없애기 위해
 *   정적 팩토리로만 만들 수 있게 했다 — 생성 방식 자체가 "정확히 1개" 규칙을 보장한다.
 * - 사용 예: agreeHistoryService.record(usrSn, AgreeType.TERMS_OF_SERVICE,
 *            AgreeActType.BID, true, AgreeRef.bid(bidSn))
 */
@Getter
public class AgreeRef {

    private Long bidSn;
    private Long aucSn;
    private Long ptLdgSn;
    private Long trdDspSn;
    private Long trdSn;

    private AgreeRef() { }

    /** 입찰 건 동의 (입찰·즉시구매) */
    public static AgreeRef bid(long bidSn) {
        AgreeRef ref = new AgreeRef();
        ref.bidSn = bidSn;
        return ref;
    }

    /** 경매 건 동의 */
    public static AgreeRef auction(long aucSn) {
        AgreeRef ref = new AgreeRef();
        ref.aucSn = aucSn;
        return ref;
    }

    /** 포인트 원장 건 동의 (홀딩·보관금 전환) */
    public static AgreeRef pointLedger(long ptLdgSn) {
        AgreeRef ref = new AgreeRef();
        ref.ptLdgSn = ptLdgSn;
        return ref;
    }

    /** 거래 문제(분쟁) 건 동의 */
    public static AgreeRef tradeDispute(long trdDspSn) {
        AgreeRef ref = new AgreeRef();
        ref.trdDspSn = trdDspSn;
        return ref;
    }

    /** 거래 건 동의 (완료 확인 등) */
    public static AgreeRef trade(long trdSn) {
        AgreeRef ref = new AgreeRef();
        ref.trdSn = trdSn;
        return ref;
    }
}
