package nct.settlement.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.point.service.PointService;
import nct.settlement.domain.Settlement;
import nct.settlement.domain.SettlementStatus;
import nct.settlement.exception.SettlementException;
import nct.settlement.mapper.SettlementMapper;
import nct.trade.dto.TradeSettlementReference;
import nct.trade.port.TradeSettlementReferenceReader;

/**
 * [정산 - 서비스 계약] (담당자6 백종남)
 *
 * 거래 도메인(담당자4)이 거래 완료를 확정한 뒤 createPending을 호출한다 (F-PAY-042).
 * 완료 처리(F-PAY-043)는 관리자 명시 처리 또는 거래 자동 완료에 따른 시스템 처리로 실행하며,
 * 보류/해제(F-PAY-044, F-OPS-079)는 거래 분쟁 접수·판정에 따라 호출한다.
 * SETTLEMENT 테이블 직접 변경 금지.
 *
 * 상태 전이: 대기 → 완료 / 대기 ↔ 보류 / 대기·보류 → 환불종결 — 행 잠금 후 검증하므로
 * 보류 중인 정산이 실수로 완료되는 사고를 원천 차단한다.
 *
 * 관리자용 REST API(/api/admin/settlement/**)는 운영 도메인 확정 후 별도 작업.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final String SYSTEM_ACTOR = "SYSTEM";
    private static final String MATERIAL_TRADE = "TRDC0001";
    private static final String SERVICE_TRADE = "TRDC0002";

    private final SettlementMapper settlementMapper;
    private final PointService pointService;
    private final NotificationService notificationService;
    private final TradeSettlementReferenceReader tradeSettlementReferenceReader;

    /** 거래 완료 → 정산 대기 전환 (F-PAY-042). @return 생성된 정산 일련번호 */
    @Transactional
    public long createPending(long trdSn, long usrSn, long amt) {
        if (trdSn <= 0 || usrSn <= 0) {
            throw new SettlementException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래번호와 정산 대상 회원번호가 필요합니다.");
        }
        if (amt <= 0) {
            throw new SettlementException(ErrorCode.SETTLEMENT_INVALID_AMOUNT,
                    "정산 금액은 0보다 커야 합니다: " + amt);
        }
        Settlement s = new Settlement();
        s.setTrdSn(trdSn);
        s.setUsrSn(usrSn);
        s.setStlmAmt(amt);
        s.setStlmStatusCd(SettlementStatus.PENDING.getCode());
        try {
            settlementMapper.insert(s);
        } catch (DuplicateKeyException duplicateKeyException) {
            Settlement existing = settlementMapper.selectByTradeForUpdate(trdSn);
            if (existing == null) {
                throw duplicateKeyException;
            }
            validateSameSettlement(existing, usrSn, amt);
            return existing.getStlmSn();
        }

        // 같은 트랜잭션 안에서 알림 — 정산 생성이 롤백되면 알림도 함께 사라진다
        notificationService.notifySettlement(usrSn, "정산 대기",
                String.format("거래대금 %,dP가 정산 대기 상태로 전환되었습니다.", amt), trdSn);
        return s.getStlmSn();
    }

    /** 관리자 명시 정산 완료 처리 (F-PAY-043). 관리자 번호는 감사 수정자로 기록한다. */
    @Transactional
    public void complete(long stlmSn, long adminUsrSn) {
        if (adminUsrSn <= 0) {
            throw new SettlementException(ErrorCode.INVALID_INPUT_VALUE,
                    "처리 관리자 회원번호가 필요합니다.");
        }
        completeInternal(stlmSn, String.valueOf(adminUsrSn));
    }

    /** 만료 거래의 자동 완료가 호출하는 시스템 정산 완료 처리. */
    @Transactional
    public void completeAutomatically(long stlmSn) {
        if (stlmSn <= 0) {
            throw new SettlementException(ErrorCode.INVALID_INPUT_VALUE,
                    "정산번호가 필요합니다.");
        }
        completeInternal(stlmSn, SYSTEM_ACTOR);
    }

    private void completeInternal(long stlmSn, String actorId) {
        Settlement s = requireStatus(stlmSn, SettlementStatus.PENDING, "완료");
        EscrowReference escrowReference = resolveEscrowReference(s.getTrdSn());

        updateStatusOrThrow(stlmSn, SettlementStatus.COMPLETED, actorId, "완료");
        long creditedAmount = pointService.creditEscrowToSettleable(
                s.getUsrSn(),
                s.getTrdSn(),
                escrowReference.refType(),
                escrowReference.refSn(),
                "정산 완료 (정산번호 " + stlmSn + ")");
        if (creditedAmount != s.getStlmAmt()) {
            throw new SettlementException(ErrorCode.CONFLICT,
                    "정산 금액과 보관금 잔액이 일치하지 않습니다. 정산번호: " + stlmSn
                            + ", 정산금액: " + s.getStlmAmt()
                            + ", 보관금: " + creditedAmount);
        }
        // 정산 적립 알림은 creditEscrowToSettleable 내부에서 함께 발행한다.
    }

    private EscrowReference resolveEscrowReference(long trdSn) {
        TradeSettlementReference reference = tradeSettlementReferenceReader.getByTradeSn(trdSn);
        if (reference.getTradeSn() != trdSn) {
            throw new SettlementException(ErrorCode.CONFLICT,
                    "거래와 정산 참조가 일치하지 않습니다: " + trdSn);
        }

        return switch (reference.getTradeTypeCode()) {
            case MATERIAL_TRADE -> {
                Long bidSn = reference.getBidSn();
                if (bidSn == null || bidSn <= 0) {
                    throw new SettlementException(ErrorCode.CONFLICT,
                            "물건 거래의 낙찰 입찰 참조가 없습니다: " + trdSn);
                }
                yield new EscrowReference(RefType.BID, bidSn);
            }
            case SERVICE_TRADE -> new EscrowReference(RefType.TRADE, trdSn);
            default -> throw new SettlementException(ErrorCode.CONFLICT,
                    "지원하지 않는 거래 유형입니다: " + reference.getTradeTypeCode());
        };
    }

    /** 거래 분쟁 접수 → 정산 보류 (F-PAY-044, ML-PAY-005): 대기 → 보류 */
    @Transactional
    public void holdUp(long stlmSn, String reason) {
        Settlement s = requireStatus(stlmSn, SettlementStatus.PENDING, "보류");
        holdSettlement(s, reason);
    }

    /**
     * 거래 분쟁 접수 트랜잭션에서 거래번호 기준으로 기존 정산을 조건부 보류한다.
     * 정산이 아직 생성되지 않은 정상적인 사전 분쟁 접수는 별도 정산 행을 만들지 않는다.
     * 호출자는 먼저 동일 거래의 TRADE 행을 잠가 자동 완료와의 경합을 직렬화해야 한다.
     *
     * @return 이번 호출에서 대기 정산을 보류로 전환했으면 true, 정산이 없거나 이미 보류면 false
     */
    @Transactional
    public boolean holdUpByTradeIfPending(long trdSn, String reason) {
        if (trdSn <= 0) {
            throw new SettlementException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래번호가 필요합니다.");
        }

        Settlement settlement = settlementMapper.selectByTradeForUpdate(trdSn);
        if (settlement == null) {
            return false;
        }
        if (SettlementStatus.ON_HOLD.getCode().equals(settlement.getStlmStatusCd())) {
            return false;
        }
        if (!SettlementStatus.PENDING.getCode().equals(settlement.getStlmStatusCd())) {
            throw new SettlementException(ErrorCode.SETTLEMENT_INVALID_STATUS,
                    "보류 처리할 수 없는 상태입니다. 거래번호: " + trdSn
                            + ", 현재 상태: " + settlement.getStlmStatusCd());
        }

        holdSettlement(settlement, reason);
        return true;
    }

    private void holdSettlement(Settlement settlement, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new SettlementException(ErrorCode.INVALID_INPUT_VALUE,
                    "정산 보류 사유가 필요합니다.");
        }

        updateStatusOrThrow(settlement.getStlmSn(), SettlementStatus.ON_HOLD, SYSTEM_ACTOR, "보류");
        notificationService.notifySettlement(settlement.getUsrSn(), "정산 보류",
                String.format("거래대금 %,dP의 정산이 보류되었습니다. 사유: %s",
                        settlement.getStlmAmt(), reason.trim()),
                settlement.getTrdSn());
    }

    /** 정산 보류 해제 (F-OPS-079): 보류 → 대기 */
    @Transactional
    public void resume(long stlmSn) {
        Settlement s = requireStatus(stlmSn, SettlementStatus.ON_HOLD, "보류 해제");

        updateStatusOrThrow(stlmSn, SettlementStatus.PENDING, SYSTEM_ACTOR, "보류 해제");
        notificationService.notifySettlement(s.getUsrSn(), "정산 보류 해제",
                String.format("거래대금 %,dP의 정산 보류가 해제되어 대기 상태로 전환되었습니다.", s.getStlmAmt()), s.getTrdSn());
    }

    /**
     * 담당자 7 · F-OPS-006 소비 계약: 분쟁 완료·반려 시 거래번호 기준으로 보류 정산을 재개한다.
     * 정산이 아직 없거나 이미 대기이면 멱등하게 false를 반환한다.
     */
    @Transactional
    public boolean resumeByTradeIfOnHold(long trdSn, long adminUsrSn) {
        validateAdminTradeCommand(trdSn, adminUsrSn);
        Settlement settlement = settlementMapper.selectByTradeForUpdate(trdSn);
        if (settlement == null || SettlementStatus.PENDING.getCode().equals(settlement.getStlmStatusCd())) {
            return false;
        }
        if (!SettlementStatus.ON_HOLD.getCode().equals(settlement.getStlmStatusCd())) {
            throw new SettlementException(ErrorCode.SETTLEMENT_INVALID_STATUS,
                    "보류 해제할 수 없는 정산 상태입니다. 거래번호: " + trdSn
                            + ", 현재 상태: " + settlement.getStlmStatusCd());
        }

        updateStatusOrThrow(
                settlement.getStlmSn(), SettlementStatus.PENDING,
                String.valueOf(adminUsrSn), "보류 해제");
        notificationService.notifySettlement(
                settlement.getUsrSn(),
                "정산 보류 해제",
                String.format("거래대금 %,dP의 정산 보류가 해제되어 대기 상태로 전환되었습니다.",
                        settlement.getStlmAmt()),
                trdSn);
        return true;
    }

    /**
     * 담당자 7 · F-OPS-006 소비 계약: 전액 환불 판정 시 미완료 정산을 환불종결한다.
     * 실제 보관금 반환 원장은 PointService가 같은 상위 트랜잭션에서 기록한다.
     */
    @Transactional
    public boolean closeRefundedByTradeIfOpen(long trdSn, long adminUsrSn) {
        validateAdminTradeCommand(trdSn, adminUsrSn);
        Settlement settlement = settlementMapper.selectByTradeForUpdate(trdSn);
        if (settlement == null || SettlementStatus.REFUNDED.getCode().equals(settlement.getStlmStatusCd())) {
            return false;
        }
        if (!SettlementStatus.PENDING.getCode().equals(settlement.getStlmStatusCd())
                && !SettlementStatus.ON_HOLD.getCode().equals(settlement.getStlmStatusCd())) {
            throw new SettlementException(ErrorCode.SETTLEMENT_INVALID_STATUS,
                    "환불종결할 수 없는 정산 상태입니다. 거래번호: " + trdSn
                            + ", 현재 상태: " + settlement.getStlmStatusCd());
        }

        updateStatusOrThrow(
                settlement.getStlmSn(), SettlementStatus.REFUNDED,
                String.valueOf(adminUsrSn), "환불종결");
        notificationService.notifySettlement(
                settlement.getUsrSn(),
                "정산 환불종결",
                "거래 환불 처리로 정산이 종료되었습니다.",
                trdSn);
        return true;
    }

    /** 회원별 정산 목록 (제공자 정산 화면용 — 컨트롤러는 화면 구현 시 추가) */
    public List<Settlement> getListByUser(long usrSn) {
        return settlementMapper.selectListByUser(usrSn);
    }

    /** 거래번호 기준 정산 단건 공개 조회 계약 */
    @Transactional(readOnly = true)
    public Settlement getSettlementByTrade(long trdSn) {
        Settlement settlement = settlementMapper.selectByTrade(trdSn);
        if (settlement == null) {
            throw new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND,
                    "거래에 연결된 정산 건이 없습니다: " + trdSn);
        }
        return settlement;
    }

    /**
     * 상태 전이 사전 검증 — 행 잠금(FOR UPDATE) 후 기대 상태인지 확인.
     * 잠금을 먼저 잡는 이유: 검증과 갱신 사이에 다른 트랜잭션이 상태를 바꾸는 틈을 없애기 위함
     */
    private Settlement requireStatus(long stlmSn, SettlementStatus expected, String action) {
        Settlement s = settlementMapper.selectForUpdate(stlmSn);
        if (s == null) {
            throw new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND,
                    "존재하지 않는 정산 건입니다: " + stlmSn);
        }
        if (!expected.getCode().equals(s.getStlmStatusCd())) {
            throw new SettlementException(ErrorCode.SETTLEMENT_INVALID_STATUS,
                    action + " 처리할 수 없는 상태입니다. 정산번호: " + stlmSn + ", 현재 상태: " + s.getStlmStatusCd());
        }
        return s;
    }

    private void validateAdminTradeCommand(long trdSn, long adminUsrSn) {
        if (trdSn <= 0 || adminUsrSn <= 0) {
            throw new SettlementException(ErrorCode.INVALID_INPUT_VALUE,
                    "거래번호와 처리 관리자 회원번호가 필요합니다.");
        }
    }

    private void validateSameSettlement(Settlement existing, long usrSn, long amt) {
        if (existing.getUsrSn() == null
                || existing.getUsrSn() != usrSn
                || existing.getStlmAmt() != amt) {
            throw new SettlementException(ErrorCode.CONFLICT,
                    "동일 거래의 기존 정산 정보와 정산 대상 또는 금액이 일치하지 않습니다: "
                            + existing.getTrdSn());
        }
    }

    private void updateStatusOrThrow(
            long stlmSn,
            SettlementStatus status,
            String actorId,
            String action) {
        if (settlementMapper.updateStatus(stlmSn, status.getCode(), actorId) == 0) {
            throw new SettlementException(ErrorCode.SETTLEMENT_INVALID_STATUS,
                    action + " 상태 변경에 실패했습니다. 정산번호: " + stlmSn);
        }
    }

    private record EscrowReference(RefType refType, long refSn) {
    }
}
