package nct.servicerequest.port;

import java.util.List;
import java.util.Map;

/**
 * 담당자 7 통합: 견적 도메인이 SERVICE_REQUEST 매퍼를 직접 읽지 않도록 제공하는 조회 계약.
 * F-SVC-005 견적 제출 시 공개 상태와 요청자·카테고리를 한 트랜잭션에서 확인한다.
 */
public interface ServiceRequestQuoteReader {

    ServiceRequestQuoteTarget requireOpenForQuote(Long svcReqSn);

    ServiceRequestQuoteTarget requireForProviderAccess(Long svcReqSn);

    void requireOwner(Long svcReqSn, Long usrSn);

    Map<Long, String> findTitles(List<Long> svcReqSnList);

    record ServiceRequestQuoteTarget(Long requesterUsrSn, Long categorySn) {
    }
}
