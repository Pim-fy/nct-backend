package nct.servicerequest.port;

import java.util.List;

/** 담당자 7 · 신고 제재: 신고 오케스트레이터가 요청서 테이블을 직접 쓰지 않도록 제공하는 계약입니다. */
public interface MemberServiceRequestEnforcementPort {

    List<ServiceRequestEnforcementImpact> pauseOwned(
            MemberServiceRequestEnforcementCommand command);

    List<ServiceRequestEnforcementImpact> cancelOwnedForPermanentSuspension(
            MemberServiceRequestEnforcementCommand command);

    boolean restore(ServiceRequestEnforcementRestoreCommand command);
}
