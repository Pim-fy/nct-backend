package nct.point.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.point.domain.PointExchangeOrder;
import nct.point.domain.PointExchangeOrderStatus;
import nct.point.dto.UserAccount;
import nct.point.exception.PointException;
import nct.point.mapper.PointExchangeOrderMapper;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 환전 - 서비스 계약] (담당자6 백종남, F-PAY-012, D-026)
 *
 * 확정된 처리 방식:
 * - 신청 즉시 정산가능(환전 가능) 포인트를 차감하고 원장에 기록한다 — 이중 신청 원천 차단
 * - 실제 계좌 입금은 관리자 수동 처리 (정본 규칙: 지급·승인 자동화 금지) — 신청자에게는
 *   "며칠 내 지급 예정" 알림만 나간다
 * - 지급완료/반려 처리(관리자)는 후속 범위 — 반려 시 복원 원장을 짝으로 기록하는 계약만 예약
 */
@Service
@RequiredArgsConstructor
public class PointExchangeService {

    private final PointExchangeOrderMapper exchangeMapper;
    private final PointService pointService;
    private final NotificationService notificationService;

    /**
     * 환전 신청. 검증 → 즉시 차감(원장) → 신청 행 기록(계좌 스냅샷 포함) → 접수 알림.
     * 전부 한 트랜잭션 — 어느 하나라도 실패하면 차감까지 전부 없던 일이 된다.
     *
     * @return 생성된 환전 주문 일련번호
     */
    @Transactional
    public long apply(long usrSn, long amt) {
        if (amt <= 0) {
            throw new PointException(ErrorCode.POINT_INVALID_AMOUNT, "환전 금액은 0보다 커야 합니다: " + amt);
        }

        // 계좌 미등록이면 신청 차단 — 관리자가 이체할 곳이 없으므로.
        // (계좌 등록 화면은 마이페이지(담당자3) 소유 — 여기서는 읽기만 한다)
        UserAccount account = exchangeMapper.selectUserAccount(usrSn);
        if (account == null || !account.isRegistered()) {
            throw new PointException(ErrorCode.EXCHANGE_ACCOUNT_NOT_REGISTERED,
                    "환전 계좌가 등록되어 있지 않습니다. 마이페이지에서 계좌를 먼저 등록해 주세요.");
        }

        // 잔액 검증 + 즉시 차감 — 회원 행 잠금 안에서 직렬화 (동시 신청 이중 차감 차단)
        long deductLdgSn = pointService.debitExchange(usrSn, amt, "환전 신청 차감");

        PointExchangeOrder order = new PointExchangeOrder();
        order.setUsrSn(usrSn);
        order.setPtExcOrdAmt(amt);
        order.setPtExcOrdStatusCd(PointExchangeOrderStatus.REQUESTED.getCode());
        order.setPtExcOrdDeductLdgSn(deductLdgSn);
        // 신청 시점 계좌 스냅샷 — 이후 회원이 계좌를 바꿔도 "신청 당시 계좌"가 남는다
        order.setPtExcOrdBankNm(account.getBankNm());
        order.setPtExcOrdAcntNo(account.getAcntNo());
        exchangeMapper.insert(order);

        // 같은 트랜잭션 안에서 접수 알림까지 기록 (충전 완료 알림과 같은 방침)
        notificationService.notifyExchangeRequest(usrSn, amt);
        return order.getPtExcOrdSn();
    }

    /** 내 환전 신청 목록 조회 (최신순 100건, 신청·완료·반려 포함) */
    @Transactional(readOnly = true)
    public List<PointExchangeOrder> getOrderList(long usrSn) {
        return exchangeMapper.selectListByUser(usrSn);
    }

    // ---------- 관리자 처리 (지급·승인 자동화 금지 — 관리자 수동 처리 계약) ----------

    /** 관리자 처리 대기 목록 — 신청 상태 건만, 오래된 순(먼저 신청한 사람 먼저 지급) */
    @Transactional(readOnly = true)
    public List<PointExchangeOrder> getRequestedListForAdmin() {
        return exchangeMapper.selectRequestedListForAdmin();
    }

    /**
     * 지급 완료 처리 — 관리자가 실제 계좌 이체를 마친 뒤 호출한다.
     * 포인트는 신청 때 이미 차감돼 있으므로 여기서는 상태·처리자만 기록하고 알림을 보낸다.
     */
    @Transactional
    public void complete(long ptExcOrdSn, long adminUsrSn) {
        PointExchangeOrder order = requireRequested(ptExcOrdSn);
        exchangeMapper.complete(ptExcOrdSn, PointExchangeOrderStatus.COMPLETED.getCode(), adminUsrSn);
        notificationService.notifyExchangeComplete(order.getUsrSn(), order.getPtExcOrdAmt());
    }

    /**
     * 반려 처리 — 차감했던 포인트를 복원(+) 원장으로 되돌리고 사유를 기록한다.
     * 복원·상태 변경·알림이 한 트랜잭션 — 복원만 되고 상태가 안 바뀌는 어긋남이 생길 수 없다.
     */
    @Transactional
    public void reject(long ptExcOrdSn, long adminUsrSn, String reason) {
        PointExchangeOrder order = requireRequested(ptExcOrdSn);
        long restoreLdgSn = pointService.restoreExchange(order.getUsrSn(), order.getPtExcOrdAmt(),
                "환전 반려 복원 (신청번호 " + ptExcOrdSn + ")");
        exchangeMapper.reject(ptExcOrdSn, PointExchangeOrderStatus.REJECTED.getCode(),
                adminUsrSn, restoreLdgSn, reason);
        notificationService.notifyExchangeReject(order.getUsrSn(), order.getPtExcOrdAmt(), reason);
    }

    /** 상태 전이 사전 검증 — 행 잠금 후 '신청' 상태인지 확인 (이중 처리·동시 처리 차단) */
    private PointExchangeOrder requireRequested(long ptExcOrdSn) {
        PointExchangeOrder order = exchangeMapper.selectForUpdateBySn(ptExcOrdSn);
        if (order == null) {
            throw new PointException(ErrorCode.EXCHANGE_ORDER_NOT_FOUND,
                    "존재하지 않는 환전 신청입니다: " + ptExcOrdSn);
        }
        if (!PointExchangeOrderStatus.REQUESTED.getCode().equals(order.getPtExcOrdStatusCd())) {
            throw new PointException(ErrorCode.EXCHANGE_ORDER_ALREADY_PROCESSED,
                    "이미 처리된 환전 신청입니다: " + ptExcOrdSn);
        }
        return order;
    }
}
