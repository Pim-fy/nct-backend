package nct.settlement.service;

import java.util.List;

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

/**
 * [정산 - 서비스 계약] (담당자6 백종남)
 *
 * 거래 도메인(담당자4)이 거래 완료를 확정한 뒤 createPending을 호출한다 (F-PAY-042).
 * 완료 처리(F-PAY-043)는 시스템/관리자가, 보류/해제(F-PAY-044, F-OPS-079)는
 * 거래 분쟁 접수·판정에 따라 호출한다. SETTLEMENT 테이블 직접 변경 금지.
 *
 * 상태 전이: 대기 → 완료 / 대기 ↔ 보류 — requireStatus가 행 잠금 후 검증하므로
 * 보류 중인 정산이 실수로 완료되는 사고를 원천 차단한다.
 *
 * 관리자용 REST API(/api/admin/settlement/**)는 운영 도메인 확정 후 별도 작업.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementMapper settlementMapper;
    private final PointService pointService;
    private final NotificationService notificationService;

    /** 거래 완료 → 정산 대기 전환 (F-PAY-042). @return 생성된 정산 일련번호 */
    @Transactional
    public long createPending(long trdSn, long usrSn, long amt) {
        if (amt <= 0) {
            throw new SettlementException(ErrorCode.SETTLEMENT_INVALID_AMOUNT,
                    "정산 금액은 0보다 커야 합니다: " + amt);
        }
        Settlement s = new Settlement();
        s.setTrdSn(trdSn);
        s.setUsrSn(usrSn);
        s.setStlmAmt(amt);
        s.setStlmStatusCd(SettlementStatus.PENDING.getCode());
        settlementMapper.insert(s);

        // 같은 트랜잭션 안에서 알림 — 정산 생성이 롤백되면 알림도 함께 사라진다
        notificationService.notifySettlement(usrSn, "정산 대기",
                String.format("거래대금 %,dP가 정산 대기 상태로 전환되었습니다.", amt), trdSn);
        return s.getStlmSn();
    }

    /** 정산 완료 처리 (F-PAY-043): 대기 → 완료, 판매자에게 정산가능 포인트 적립 */
    @Transactional
    public void complete(long stlmSn) {
        Settlement s = requireStatus(stlmSn, SettlementStatus.PENDING, "완료");

        settlementMapper.updateStatus(stlmSn, SettlementStatus.COMPLETED.getCode());
        // 보관금전환으로 빠졌던 거래대금이 판매자의 정산가능 버킷에 도착하는 지점
        pointService.creditSettleable(s.getUsrSn(), s.getStlmAmt(),
                RefType.TRADE, s.getTrdSn(), "정산 완료 (정산번호 " + stlmSn + ")");

        notificationService.notifySettlement(s.getUsrSn(), "정산 완료",
                String.format("%,dP가 정산 가능 포인트로 적립되었습니다.", s.getStlmAmt()), s.getTrdSn());
    }

    /** 거래 분쟁 접수 → 정산 보류 (F-PAY-044, ML-PAY-005): 대기 → 보류 */
    @Transactional
    public void holdUp(long stlmSn, String reason) {
        Settlement s = requireStatus(stlmSn, SettlementStatus.PENDING, "보류");

        settlementMapper.updateStatus(stlmSn, SettlementStatus.ON_HOLD.getCode());
        notificationService.notifySettlement(s.getUsrSn(), "정산 보류",
                String.format("거래대금 %,dP의 정산이 보류되었습니다. 사유: %s", s.getStlmAmt(), reason), s.getTrdSn());
    }

    /** 정산 보류 해제 (F-OPS-079): 보류 → 대기 */
    @Transactional
    public void resume(long stlmSn) {
        Settlement s = requireStatus(stlmSn, SettlementStatus.ON_HOLD, "보류 해제");

        settlementMapper.updateStatus(stlmSn, SettlementStatus.PENDING.getCode());
        notificationService.notifySettlement(s.getUsrSn(), "정산 보류 해제",
                String.format("거래대금 %,dP의 정산 보류가 해제되어 대기 상태로 전환되었습니다.", s.getStlmAmt()), s.getTrdSn());
    }

    /** 회원별 정산 목록 (제공자 정산 화면용 — 컨트롤러는 화면 구현 시 추가) */
    public List<Settlement> getListByUser(long usrSn) {
        return settlementMapper.selectListByUser(usrSn);
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
}
