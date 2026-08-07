package nct.servicerequest.mapper;

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
}
