package nct.servicerequest.port;

import nct.servicerequest.dto.AdminServiceRequestDetail;
import nct.servicerequest.dto.AdminServiceRequestPage;
import nct.servicerequest.dto.AdminServiceRequestSearchCondition;

/**
 * 담당자 7 · 관리자 서비스 요청 조회 계약.
 * 관리자 도메인이 SERVICE_REQUEST Mapper에 직접 접근하지 않고 목록과 상세를 조회할 때 사용한다.
 */
public interface AdminServiceRequestReader {

    AdminServiceRequestPage readPage(AdminServiceRequestSearchCondition condition);

    AdminServiceRequestDetail readDetail(Long serviceRequestId);
}
