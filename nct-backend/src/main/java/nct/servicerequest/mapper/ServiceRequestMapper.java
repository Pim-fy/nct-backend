package nct.servicerequest.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.servicerequest.domain.ServiceRequest;
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

    Optional<ServiceRequestResponse> findServiceRequestById(@Param("svcReqSn") Long svcReqSn);

    List<ServiceRequestResponse> findMyServiceRequests(@Param("usrSn") Long usrSn, @Param("filterType") String filterType);

    Optional<ServiceRequest> findServiceRequestEntityById(@Param("svcReqSn") Long svcReqSn);

    void updateServiceRequest(ServiceRequest serviceRequest);

    int closeServiceRequest(@Param("svcReqSn") Long svcReqSn, @Param("usrSn") Long usrSn, @Param("updtId") String updtId);

    void deleteServiceRequest(@Param("svcReqSn") Long svcReqSn, @Param("usrSn") Long usrSn);
}
