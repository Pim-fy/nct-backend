package nct.trade.dto;

import lombok.Getter;
import lombok.Setter;

/** 상대방이 대기 중인 서비스 일정 취소 요청에 내리는 결정이다. */
@Getter
@Setter
public class ServiceScheduleCancellationDecisionRequest {

    private boolean approved;
}
