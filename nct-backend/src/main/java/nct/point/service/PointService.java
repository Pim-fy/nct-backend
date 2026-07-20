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
 * 이 서비스의 계약 메서드(hold / releaseHold / convertHoldToEscrow /
 * debitEscrow / creditEscrowToSettleable / refundEscrow)만 호출한다.
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
     *
     * 수수료 정책 (F-PAY-008/009):
     * - MVP 거래 수수료는 0원 — 그래서 거래대금 "전액"이 그대로 적립되며, 이게 빠뜨린 게 아니라
     *   의도된 동작임을 원장 사유에 "(수수료 0원)"으로 명시해 기록한다.
     * - 향후 수수료를 도입할 때의 확장 지점: 원장유형 공통코드(PTLG02)에 '수수료' 유형을 추가하고
     *   여기서 적립(+전액)과 수수료 차감(-수수료) 두 행을 짝으로 기록하면 된다. 요율은 이미
     *   SYSTEM_SETTING.SYS_SET_SVC_FEE_RATE(관리자 시스템 설정 화면)에서 관리되고 있다.
     */
    @Transactional
    public void creditSettleable(long usrSn, long amt, RefType refType, long refSn, String reason) {
        requirePositive(amt);
        lockUser(usrSn);

        PointBalance bal = pointMapper.selectBalance(usrSn);
        insertLedger(usrSn, PointCategory.SETTLEABLE, PointLedgerType.SETTLE, amt,
                bal.getSettleableAmt() + amt, refType, refSn, reason + " (수수료 0원)");
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
     * 환전 반려 복원 (F-PAY-012).
     * 신청 즉시 차감했던 금액을 정산가능 버킷에 +원장으로 되돌린다 — 차감 원장과 짝(합계 0).
     *
     * @return 생성된 포인트원장일련번호 (환전 주문이 복원 원장으로 연결해 둔다)
     */
    @Transactional
    public long restoreExchange(long usrSn, long amt, String reason) {
        requirePositive(amt);
        lockUser(usrSn);

        PointBalance bal = pointMapper.selectBalance(usrSn);
        return insertLedger(usrSn, PointCategory.SETTLEABLE, PointLedgerType.EXCHANGE_RESTORE, amt,
                bal.getSettleableAmt() + amt, null, null, reason);
    }

    /**
     * 정산가능→사용가능 전환 (F-PAY-010).
     * 판매·서비스로 번 정산가능 포인트를 현금 환전 대신 플랫폼 안에서 다시 쓸 수 있게
     * 사용가능 버킷으로 옮긴다. 지갑 화면에서 사용자가 직접 신청한다.
     *
     * 자동 검증 조건 (사용자 결정 2026-07-18): "분쟁 없음 확인 후 전환" —
     * 신청자가 당사자인 거래에 진행 중인 거래 문제(접수·처리중)가 있으면 거부한다.
     * 정산가능 포인트가 환불·조정 재원이 될 수 있어서, 분쟁 판정 전에 빠져나가는 것을 막는다.
     */
    @Transactional
    public void convertSettleableToAvailable(long usrSn, long amt) {
        requirePositive(amt);
        lockUser(usrSn);

        // 분쟁 없음 확인 — 진행 중(접수·처리중) 거래 문제가 하나라도 있으면 전환 자체를 차단
        int activeDisputes = pointMapper.countActiveDisputes(usrSn);
        if (activeDisputes > 0) {
            throw new PointException(ErrorCode.POINT_CONVERT_BLOCKED_BY_DISPUTE,
                    "진행 중인 거래 문제가 " + activeDisputes + "건 있어 전환할 수 없습니다.");
        }

        PointBalance bal = pointMapper.selectBalance(usrSn);
        if (bal.getSettleableAmt() < amt) {
            throw new PointException(ErrorCode.POINT_INSUFFICIENT,
                    "전환 가능한 정산가능 포인트가 부족합니다. 신청: " + amt + "P, 보유: " + bal.getSettleableAmt() + "P");
        }

        // 복식 기록: 정산가능에서 빠져나가(−) 사용가능으로 들어온다(+) — 합계 0, 총 보유 불변
        String reason = "정산가능→사용가능 전환";
        insertLedger(usrSn, PointCategory.SETTLEABLE, PointLedgerType.CONVERT, -amt,
                bal.getSettleableAmt() - amt, null, null, reason);
        insertLedger(usrSn, PointCategory.AVAILABLE, PointLedgerType.CONVERT, amt,
                bal.getAvailableAmt() + amt, null, null, reason);

        // 같은 트랜잭션 안에서 알림까지 기록 (원장만 남고 알림이 누락되는 일이 없도록)
        notificationService.notifyPointConvert(usrSn, amt);
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

    // ---------- 보관금(에스크로) 계약 (F-SVC-013 / F-SVC-015 / 분쟁 환불 — 2026-07-20) ----------

    /**
     * 서비스 보관금 생성 (F-SVC-013). 견적 선택 시 거래 담당자(4)가 호출한다.
     * 의뢰자의 사용가능 포인트에서 견적 금액을 바로 보관금으로 분리한다 —
     * 경매(convertHoldToEscrow)는 입찰 때 잡아둔 홀딩에서 빠지지만, 서비스는 선행 홀딩이
     * 없어서 사용가능에서 직접 빠지는 것만 다르고 "회원 잔액 밖으로 분리"라는 의미는 같다.
     *
     * 호출 규칙 (팀 전달문 참조): TRADE 생성과 이 메서드를 한 트랜잭션으로 묶을 것 —
     * 보관금 차감이 실패하면 거래 생성까지 함께 롤백되어, 정본 게이트 조건인
     * "보관금 없는 진행 거래 0건"이 별도 상태 컬럼 없이 구조적으로 보장된다.
     *
     * @param refType 보관금 사유가 된 참조 유형 (서비스 거래면 TRADE)
     * @param refSn   참조 일련번호 (거래일련번호)
     */
    @Transactional
    public void debitEscrow(long usrSn, long amt, RefType refType, long refSn, String reason) {
        requirePositive(amt);
        lockUser(usrSn);

        // 같은 참조 건에 살아있는 보관금이 있으면 중복 — 이중 결제 방지 (합산이 음수 = 잔존)
        if (pointMapper.selectActiveEscrowAmtByMemberRef(usrSn, refType.getCode(), refSn) < 0) {
            throw new PointException(ErrorCode.POINT_DUPLICATE_ESCROW,
                    "이미 보관금이 결제된 건입니다. 참조: " + refType + "-" + refSn);
        }
        // 서버 측 잔액 재검증 — 프론트 검증은 신뢰하지 않는다
        PointBalance bal = pointMapper.selectBalance(usrSn);
        if (bal.getAvailableAmt() < amt) {
            throw new InsufficientPointException(amt, bal.getAvailableAmt());
        }

        // 단식 1행(−): 회원의 총 보유가 줄고 거래대금이 플랫폼 보관 상태가 된다 (경매 보관금전환과 동일 유형)
        insertLedger(usrSn, PointCategory.AVAILABLE, PointLedgerType.ESCROW, -amt,
                bal.getAvailableAmt() - amt, refType, refSn, reason);
        // 알림 없음 — 사용자 본인이 화면에서 직접 결제한 능동 행위 (홀딩과 동일 원칙)
    }

    /**
     * 보관금 → 정산가능 전환 (F-SVC-015). 양측 완료 확인 또는 5일 자동 완료 시
     * 서비스 거래 담당자(5)가 호출한다. 보관금으로 잡혀 있던 거래대금을 제공자의
     * 정산가능 버킷에 적립한다 (분쟁 접수 건은 차단 — "분쟁 접수 시 정산 보류" 정본 규칙).
     *
     * @param providerSn 정산대금을 받을 제공자 회원번호
     * @param refType    보관금을 만들 때 쓴 참조 유형 (서비스 거래면 TRADE)
     * @param refSn      참조 일련번호 (거래일련번호)
     * @return 적립된 금액 (보관금 전액 — 수수료 0원 정책)
     */
    @Transactional
    public long creditEscrowToSettleable(long providerSn, RefType refType, long refSn, String reason) {
        lockUser(providerSn);

        // 분쟁 접수 시 정산 보류 (F-SVC-015) — 해당 거래에 진행 중 거래 문제가 있으면 전환 자체를 차단.
        // 참조가 거래(TRADE)일 때만 검사 가능 — 서비스 거래 호출은 항상 TRADE 참조를 쓴다
        if (refType == RefType.TRADE) {
            int activeDisputes = pointMapper.countActiveDisputesByTrade(refSn);
            if (activeDisputes > 0) {
                throw new PointException(ErrorCode.POINT_SETTLE_BLOCKED_BY_DISPUTE,
                        "해당 거래에 진행 중인 거래 문제가 " + activeDisputes + "건 있어 정산 전환할 수 없습니다.");
            }
        }

        // 살아있는 보관금 확인 — 제공자 쪽 호출이라 지불자를 모르므로 참조 건만으로 찾는다 (음수 = 잔존)
        long escrowNet = pointMapper.selectActiveEscrowAmtByRef(refType.getCode(), refSn);
        if (escrowNet >= 0) {
            throw new PointException(ErrorCode.POINT_ESCROW_NOT_FOUND,
                    "정산 전환할 보관금이 없습니다. 참조: " + refType + "-" + refSn);
        }
        // 같은 건으로 이미 정산 지급됐으면 이중 지급 — 원장만으로 판정 (SETTLE 합산 > 0)
        if (pointMapper.selectSettledAmtByRef(refType.getCode(), refSn) > 0) {
            throw new PointException(ErrorCode.POINT_ESCROW_ALREADY_SETTLED,
                    "이미 정산 지급이 끝난 건입니다. 참조: " + refType + "-" + refSn);
        }

        long amt = -escrowNet; // 보관금 잔존액 = 적립할 금액 (호출자가 금액을 잘못 넘길 여지 자체를 없앤다)
        PointBalance bal = pointMapper.selectBalance(providerSn);
        // 수수료 0원 정책(F-PAY-008/009) — creditSettleable과 같은 명시 규칙으로 기록
        insertLedger(providerSn, PointCategory.SETTLEABLE, PointLedgerType.SETTLE, amt,
                bal.getSettleableAmt() + amt, refType, refSn, reason + " (수수료 0원)");

        // 같은 트랜잭션 안에서 알림까지 기록 — 정산대금을 받는 쪽(제공자 업무)이라 PROVIDER 구분
        notificationService.notifyEscrowSettled(providerSn, amt, refType, refSn);
        return amt;
    }

    /**
     * 분쟁 판정 보관금 환불. 거래 문제 판정이 "환불"로 확정될 때 분쟁 담당자(4·5)가 호출한다.
     * 보관금으로 빠져 있던 거래대금 전액을 구매자/의뢰자의 사용가능 버킷에 되돌린다 —
     * 물건 거래(보관금 참조 BID)·서비스 거래(참조 TRADE) 공통 계약.
     *
     * 원장은 수정·삭제하지 않으므로(기록 불변) 환불(+) 행을 짝으로 남기는 방식이다 —
     * 같은 참조의 보관금전환(−)과 합산이 0이 되어 이중 환불이 원장만으로 차단된다.
     *
     * @param usrSn 보관금을 냈던 구매자/의뢰자 회원번호 (다른 회원을 넘기면 보관금이 안 잡혀 실패한다)
     * @return 환불된 금액
     */
    @Transactional
    public long refundEscrow(long usrSn, RefType refType, long refSn, String reason) {
        lockUser(usrSn);

        // 이 회원 앞으로 살아있는 보관금이 있어야 한다 (없으면 이미 환불됐거나 잘못된 호출)
        long escrowNet = pointMapper.selectActiveEscrowAmtByMemberRef(usrSn, refType.getCode(), refSn);
        if (escrowNet >= 0) {
            throw new PointException(ErrorCode.POINT_ESCROW_NOT_FOUND,
                    "환불할 보관금이 없습니다. 참조: " + refType + "-" + refSn);
        }
        // 이미 판매자/제공자에게 정산 지급된 돈은 여기서 되돌릴 수 없다 — 관리자 수동 보정 영역
        if (pointMapper.selectSettledAmtByRef(refType.getCode(), refSn) > 0) {
            throw new PointException(ErrorCode.POINT_ESCROW_ALREADY_SETTLED,
                    "이미 정산 지급이 끝나 환불할 수 없습니다. 참조: " + refType + "-" + refSn);
        }

        long amt = -escrowNet; // 보관금 잔존액 전액 환불
        PointBalance bal = pointMapper.selectBalance(usrSn);
        insertLedger(usrSn, PointCategory.AVAILABLE, PointLedgerType.REFUND, amt,
                bal.getAvailableAmt() + amt, refType, refSn, reason);

        // 같은 트랜잭션 안에서 알림까지 기록. 판정 내용 이메일은 기존 notifyDisputeResolved
        // 트리거(분쟁 담당자 호출)의 몫이라 여기서는 지갑 알림만 보낸다 (이메일 중복 방지)
        notificationService.notifyPointRefund(usrSn, amt, refType, refSn, reason);
        return amt;
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
