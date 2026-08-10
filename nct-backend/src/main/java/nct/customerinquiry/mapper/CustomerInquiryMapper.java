package nct.customerinquiry.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.customerinquiry.domain.CustomerInquiry;
import nct.customerinquiry.dto.AdminCustomerInquiryDetailResponse;
import nct.customerinquiry.dto.AdminCustomerInquiryListItemResponse;
import nct.customerinquiry.dto.CustomerInquiryDetailResponse;
import nct.customerinquiry.dto.CustomerInquiryListItemResponse;

/** 담당자 7 · CUSTOMER_INQUIRY 전용 MyBatis 저장·조회 경계다. */
@Mapper
public interface CustomerInquiryMapper {

    int insertPlaceholder(CustomerInquiry inquiry);

    int updateMaskedContent(
            @Param("inquirySn") Long inquirySn,
            @Param("userSn") Long userSn,
            @Param("title") String title,
            @Param("content") String content,
            @Param("actorId") String actorId);

    List<CustomerInquiryListItemResponse> findMyInquiries(
            @Param("userSn") Long userSn,
            @Param("statusCode") String statusCode,
            @Param("offset") long offset,
            @Param("size") int size);

    long countMyInquiries(
            @Param("userSn") Long userSn,
            @Param("statusCode") String statusCode);

    CustomerInquiryDetailResponse findMyInquiryDetail(
            @Param("inquirySn") Long inquirySn,
            @Param("userSn") Long userSn);

    List<AdminCustomerInquiryListItemResponse> findAdminInquiries(
            @Param("statusCode") String statusCode,
            @Param("inquiryTypeCode") String inquiryTypeCode,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("size") int size);

    long countAdminInquiries(
            @Param("statusCode") String statusCode,
            @Param("inquiryTypeCode") String inquiryTypeCode,
            @Param("keyword") String keyword);

    AdminCustomerInquiryDetailResponse findAdminInquiryDetail(
            @Param("inquirySn") Long inquirySn);

    int startProcessing(
            @Param("inquirySn") Long inquirySn,
            @Param("adminUserSn") Long adminUserSn,
            @Param("actorId") String actorId,
            @Param("receivedStatusCode") String receivedStatusCode,
            @Param("processingStatusCode") String processingStatusCode);

    int completeAnswer(
            @Param("inquirySn") Long inquirySn,
            @Param("adminUserSn") Long adminUserSn,
            @Param("answer") String answer,
            @Param("actorId") String actorId,
            @Param("processingStatusCode") String processingStatusCode,
            @Param("answeredStatusCode") String answeredStatusCode);
}
