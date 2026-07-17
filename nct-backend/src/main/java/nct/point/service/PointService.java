package nct.point.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.point.domain.PointBalance;
import nct.point.domain.PointCategory;
import nct.point.domain.PointLedger;
import nct.point.domain.PointLedgerType;
import nct.point.exception.DuplicateHoldException;
import nct.point.exception.InsufficientPointException;
import nct.point.exception.PointException;
import nct.point.mapper.PointMapper;

/**
 * [포인트 원장 - 서비스 계약] (담당자6 백종남)
 *
 * 다른 도메인(입찰·경매·거래)은 BID/AUCTION/TRADE 상태를 확정한 뒤
 * 이 서비스의 계약 메서드(hold / releaseHold / convertHoldToEscrow)만 호출한다.
 * 포인트 원장(POINT_LEDGER)은 이 서비스 외부에서 직접 변경하지 않는다.
 *
 * 핵심 설계:
 * - 잔액 = 원장 SUM (잔액 컬럼 없음). 원장은 INSERT만 하고 수정·삭제하지 않는다
 * - 이동(홀딩/반환)은 복식 기록: 출발 버킷 −금액 + 도착 버킷 +금액, 두 행의 합이 0
 * - 모든 명령은 시작 시 lockUser(회원 행 잠금)로 동시 요청을 직렬화 → 이중 차감 방지
 * - 호출하는 쪽 트랜잭션 안에서 부르면 같은 트랜잭션으로 묶인다 (실패 시 함께 롤백)
 *
 * 감사로그(AUDIT_LOG)는 담당자7 도메인 구현 후 연동한다.
 */
@Service
@RequiredArgsConstructor
public class PointService {

    private final PointMapper pointMapper;
    private final NotificationService notificationService;

    // ---------- 조회 (F-PAY-038, F-PAY-039) ----------

    /** 잔액 조회 — 포인트분류(사용가능/홀딩/정산가능)별 원장 합계 */
    public PointBalance getBalance(long usrSn) {
        return pointMapper.selectBalance(usrSn);
    }

    /** 원장 목록 조회 — 공통코드 한글명 포함, 최신순 100건 */
    public List<PointLedger> getLedgerList(long usrSn) {
        return pointMapper.selectLedgerList(usrSn);
    }

    // ---------- 명령 계약 ----------

    /**
     * 포인트 홀딩 (ML-PAY-001, ML-PAY-002).
     * 사용가능 잔액을 검증하고 참조 건 기준 중복 홀딩을 차단한 뒤
     * 사용가능 -amt / 홀딩 +amt 원장 두 행을 기록한다.
     *
     * @param refType 홀딩 사유가 된 참조 유형 (입찰이면 BID)
     * @param refSn   참조 일련번호
     */
    @Transactional
    public void hold(long usrSn, long amt, RefType refType, long refSn, String reason) {
        requirePositive(amt);
        lockUser(usrSn); // 이 시점부터 트랜잭션 끝까지 같은 회원의 다른 포인트 요청은 대기

        // 같은 참조 건(예: 같은 입찰)에 유효 홀딩이 있으면 중복 — 이중 차감 방지
        if (pointMapper.selectActiveHoldAmtByRef(usrSn, refType.getCode(), refSn) > 0) {
            throw new DuplicateHoldException(refType, refSn);
        }
        // 서버 측 잔액 재검증 — 프론트 검증은 신뢰하지 않는다
        PointBalance bal = pointMapper.selectBalance(usrSn);
        if (bal.getAvailableAmt() < amt) {
            throw new InsufficientPointException(amt, bal.getAvailableAmt());
        }

        // 복식 기록: 사용가능에서 빠져나가고(−) 홀딩으로 들어온다(+) — 합계 0, 총 보유 불변
        insertLedger(usrSn, PointCategory.AVAILABLE, PointLedgerType.HOLD, -amt,
                bal.getAvailableAmt() - amt, refType, refSn, reason);
        insertLedger(usrSn, PointCategory.HOLD, PointLedgerType.HOLD, amt,
                bal.getHoldAmt() + amt, refType, refSn, reason);
    }

    /**
     * 홀딩 반환 (ML-PAY-003). 최고 입찰자 변경·유찰·취소 승인 시 호출한다.
     * 참조 건에 남은 홀딩 전액을 사용가능으로 되돌리고 반환 알림을 보낸다.
     *
     * @return 반환된 금액
     */
    @Transactional
    public long releaseHold(long usrSn, RefType refType, long refSn, String reason) {
        lockUser(usrSn);

        long holdAmt = pointMapper.selectActiveHoldAmtByRef(usrSn, refType.getCode(), refSn);
        if (holdAmt <= 0) {
            throw new PointException(ErrorCode.POINT_HOLD_NOT_FOUND,
                    "반환할 홀딩이 없습니다. 참조: " + refType + "-" + refSn);
        }
        PointBalance bal = pointMapper.selectBalance(usrSn);

        // 복식 기록: 홀딩에서 빠져나가(−) 사용가능으로 복귀(+)
        insertLedger(usrSn, PointCategory.HOLD, PointLedgerType.RELEASE, -holdAmt,
                bal.getHoldAmt() - holdAmt, refType, refSn, reason);
        insertLedger(usrSn, PointCategory.AVAILABLE, PointLedgerType.RELEASE, holdAmt,
                bal.getAvailableAmt() + holdAmt, refType, refSn, reason);

        // 같은 트랜잭션 안에서 알림까지 기록 — 원장만 남고 알림이 누락되는 일이 없도록
        notificationService.notifyPointRelease(usrSn, holdAmt, refType, refSn, reason);
        return holdAmt;
    }

    /**
     * 거래 보관금 전환 (ML-PAY-004). 낙찰·즉시구매 확정 시 호출한다.
     * 참조 건의 홀딩 전액을 회원 잔액에서 분리해 플랫폼 보관 상태로 만든다.
     * 단식 1행(−)만 기록하므로 이 시점에 회원의 총 보유가 줄어든다 —
     * 빠져나간 거래대금은 이후 SETTLEMENT가 추적한다.
     *
     * @return 전환된 금액
     */
    @Transactional
    public long convertHoldToEscrow(long usrSn, RefType refType, long refSn, String reason) {
        lockUser(usrSn);

        long holdAmt = pointMapper.selectActiveHoldAmtByRef(usrSn, refType.getCode(), refSn);
        if (holdAmt <= 0) {
            throw new PointException(ErrorCode.POINT_HOLD_NOT_FOUND,
                    "보관금으로 전환할 홀딩이 없습니다. 참조: " + refType + "-" + refSn);
        }
        PointBalance bal = pointMapper.selectBalance(usrSn);

        insertLedger(usrSn, PointCategory.HOLD, PointLedgerType.ESCROW, -holdAmt,
                bal.getHoldAmt() - holdAmt, refType, refSn, reason);
        return holdAmt;
    }

    /**
     * 정산가능 포인트 적립 (F-PAY-043). 정산 완료 시 SettlementService가 호출한다.
     * 판매자의 정산가능 버킷에 +금액 단식 1행 — 보관금전환으로 빠졌던 금액이 판매자에게 도착하는 지점.
     */
    @Transactional
    public void creditSettleable(long usrSn, long amt, RefType refType, long refSn, String reason) {
        requirePositive(amt);
        lockUser(usrSn);

        PointBalance bal = pointMapper.selectBalance(usrSn);
        insertLedger(usrSn, PointCategory.SETTLEABLE, PointLedgerType.SETTLE, amt,
                bal.getSettleableAmt() + amt, refType, refSn, reason);
    }

    /**
     * 포인트 충전 적립 (F-PG-01). PG 결제 승인 완료 후 PointChargeService가 호출한다.
     * 사용가능 버킷에 +금액 단식 1행 — 충전은 다른 도메인 이벤트를 참조하지 않으므로 REF가 없다.
     *
     * @return 생성된 포인트원장일련번호 (POINT_CHARGE_ORDER가 완료 처리 시 연결해 둔다)
     */
    @Transactional
    public long creditCharge(long usrSn, long amt, String reason) {
        requirePositive(amt);
        lockUser(usrSn);

        PointBalance bal = pointMapper.selectBalance(usrSn);
        return insertLedger(usrSn, PointCategory.AVAILABLE, PointLedgerType.CHARGE, amt,
                bal.getAvailableAmt() + amt, null, null, reason);
    }

    /**
     * 환전 신청 차감 (F-PAY-012, D-026 — 신청 즉시 차감 정책).
     * 정산가능(=환전 가능) 버킷에서 -금액 원장을 기록한다. 잔액 검증→차감이 회원 행 잠금
     * 안에서 직렬화되므로, 동시에 두 번 신청해도 같은 돈이 두 번 빠져나갈 수 없다.
     *
     * @return 생성된 포인트원장일련번호 (환전 주문이 차감 원장으로 연결해 둔다)
     */
    @Transactional
    public long debitExchange(long usrSn, long amt, String reason) {
        requirePositive(amt);
        lockUser(usrSn);

        PointBalance bal = pointMapper.selectBalance(usrSn);
        if (bal.getSettleableAmt() < amt) {
            throw new PointException(ErrorCode.POINT_INSUFFICIENT,
                    "환전 가능 포인트가 부족합니다. 신청: " + amt + "P, 보유: " + bal.getSettleableAmt() + "P");
        }
        return insertLedger(usrSn, PointCategory.SETTLEABLE, PointLedgerType.EXCHANGE_OUT, -amt,
                bal.getSettleableAmt() - amt, null, null, reason);
    }

    /**
     * 충전 회수 (D-027 보상 전용).
     * PG 승인은 성공했는데 내부 반영이 중간에 실패해 결제를 자동취소할 때,
     * 이미 지급된 충전 포인트를 되돌린다. 원장은 절대 수정·삭제하지 않으므로(기록 불변)
     * 반대 부호의 보정(-) 행을 짝으로 남기는 방식이다 — 합계가 0이 되어 잔액이 원상복구된다.
     */
    @Transactional
    public void reverseCharge(long usrSn, long amt, String reason) {
        requirePositive(amt);
        lockUser(usrSn);

        PointBalance bal = pointMapper.selectBalance(usrSn);
        insertLedger(usrSn, PointCategory.AVAILABLE, PointLedgerType.ADJUST, -amt,
                bal.getAvailableAmt() - amt, null, null, reason);
    }

    // ---------- 내부 ----------

    /** 회원 행 잠금 — 존재하지 않는 회원이면 즉시 실패 */
    private void lockUser(long usrSn) {
        if (pointMapper.lockUser(usrSn) == null) {
            throw new PointException(ErrorCode.USER_NOT_FOUND, "존재하지 않는 회원입니다: " + usrSn);
        }
    }

    /** 금액은 항상 양수로 받는다 (부호는 원장 기록 시점에 의미에 따라 부여) */
    private void requirePositive(long amt) {
        if (amt <= 0) {
            throw new PointException(ErrorCode.POINT_INVALID_AMOUNT, "금액은 0보다 커야 합니다: " + amt);
        }
    }

    /**
     * 원장 행 1건 기록 — 행위자·참조·처리후잔액 스냅샷까지 빠짐없이 남긴다 (로그 기록 원칙)
     * refType은 nullable — 충전처럼 다른 도메인 이벤트를 참조하지 않는 원장 행에 쓴다.
     *
     * @return 생성된 포인트원장일련번호
     */
    private long insertLedger(long usrSn, PointCategory category, PointLedgerType type,
                              long amt, long balAfter, RefType refType, Long refSn, String reason) {
        PointLedger row = new PointLedger();
        row.setUsrSn(usrSn);
        row.setActorUsrSn(usrSn);
        row.setPtLdgRefTypeCd(refType != null ? refType.getCode() : null);
        row.setPtLdgRefSn(refSn);
        row.setPtLdgPtTypeCd(category.getCode());
        row.setPtLdgTypeCd(type.getCode());
        row.setPtLdgAmt(amt);
        row.setPtLdgBalAfterAmt(balAfter);
        row.setPtLdgRsnCn(reason);
        pointMapper.insertLedger(row);
        return row.getPtLdgSn();
    }
}
