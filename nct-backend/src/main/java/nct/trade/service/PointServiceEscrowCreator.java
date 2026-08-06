package nct.trade.service;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.point.service.PointService;
import nct.trade.port.ServiceEscrowCreateCommand;
import nct.trade.port.ServiceEscrowCreator;

/**
 * 담당자 7 통합, F-SVC-013: 거래 조정기가 POINT_LEDGER를 직접 쓰지 않도록 PointService 계약에 연결한다.
 * 잔액, 중복 보관금, 롤백 검증은 PointService가 담당하며 이 어댑터는 상위 트랜잭션에 참여한다.
 */
@Component
@RequiredArgsConstructor
public class PointServiceEscrowCreator implements ServiceEscrowCreator {

    private static final String ESCROW_REASON = "견적 선택 보관금";

    private final PointService pointService;

    @Override
    public void createEscrow(ServiceEscrowCreateCommand command) {
        pointService.debitEscrow(
                command.requesterUserId(),
                command.amount(),
                RefType.TRADE,
                command.tradeId(),
                ESCROW_REASON);
    }
}
