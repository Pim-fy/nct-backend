package nct.servicerequest.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.servicerequest.domain.ServiceRequest;
import nct.servicerequest.dto.AdminServiceRequestDetail;
import nct.servicerequest.dto.AdminServiceRequestListItem;
import nct.servicerequest.dto.AdminServiceRequestSearchCondition;
import nct.servicerequest.dto.ServiceRequestResponse;

@Mapper
public interface ServiceRequestMapper {

    void saveServiceRequest(ServiceRequest serviceRequest);

    List<ServiceRequestResponse> searchServiceRequests(
            @Param("keyword")     String keyword,
            @Param("categorySn")  Long   categorySn,
            @Param("minBudget")   Long   minBudget,
            @Param("maxBudget")   Long   maxBudget,
            @Param("sort")        String sort);

    long countAdminServiceRequests(
            @Param("condition") AdminServiceRequestSearchCondition condition);

    List<AdminServiceRequestListItem> findAdminServiceRequestPage(
            @Param("condition") AdminServiceRequestSearchCondition condition);

    Optional<AdminServiceRequestDetail> findAdminServiceRequestDetail(
            @Param("serviceRequestId") Long serviceRequestId);

    Optional<ServiceRequestResponse> findServiceRequestById(@Param("svcReqSn") Long svcReqSn);

    Optional<ServiceRequestResponse> findPublicServiceRequestById(@Param("svcReqSn") Long svcReqSn);

    List<ServiceRequestResponse> findServiceRequestTitles(@Param("svcReqSnList") List<Long> svcReqSnList);

    List<ServiceRequestResponse> findMyServiceRequests(@Param("usrSn") Long usrSn, @Param("filterType") String filterType);

    Optional<ServiceRequest> findServiceRequestEntityById(@Param("svcReqSn") Long svcReqSn);

    Optional<ServiceRequest> findServiceRequestEntityByIdForUpdate(@Param("svcReqSn") Long svcReqSn);

    int updateServiceRequest(ServiceRequest serviceRequest);

    int markServiceRequestMatched(
            @Param("svcReqSn") Long svcReqSn,
            @Param("usrSn") Long usrSn,
            @Param("updtId") String updtId);

    int closeServiceRequest(@Param("svcReqSn") Long svcReqSn, @Param("usrSn") Long usrSn, @Param("updtId") String updtId);

    void deleteServiceRequest(@Param("svcReqSn") Long svcReqSn, @Param("usrSn") Long usrSn);

    /** 견적 요청 기간(공개 후 5일, 희망일이 더 빠르면 희망일) 만료된 공개 요청서 목록 */
    List<Long> findExpiredOpenServiceRequestIds(@Param("limit") int limit);

    /** 견적 요청 기간 만료 자동 마감 — 시스템 배치 전용, 소유자 검증 없이 상태만 확인 */
    int autoCloseServiceRequest(@Param("svcReqSn") Long svcReqSn);

    /** 마감(SVCC0004) 후 일정 기간 지난 요청서 목록 — 자동 삭제 배치 전용 */
    List<Long> findExpiredClosedServiceRequestIds(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    /** 마감 후 일정 기간 경과 요청서 자동 삭제 — 시스템 배치 전용, 소유자 검증 없음 */
    void deleteExpiredClosedServiceRequest(@Param("svcReqSn") Long svcReqSn);
}
