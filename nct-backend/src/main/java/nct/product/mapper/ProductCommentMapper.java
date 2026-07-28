package nct.product.mapper;

import java.util.List;
import java.util.Optional;
import nct.product.domain.ProductComment;
import nct.product.dto.InquiryReportTarget;
import nct.product.dto.ProductCommentResponse;
import nct.product.dto.ProductInquiryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductCommentMapper {

    void insertComment(ProductComment comment);

    List<ProductCommentResponse> findLatestComments(@Param("prdSn") Long prdSn, @Param("limit") int limit);

    void insertInquiry(ProductComment comment);

    void insertReply(ProductComment comment);

    List<ProductInquiryResponse> findInquiries(@Param("prdSn") Long prdSn);

    Optional<ProductComment> findInquiryById(@Param("inquirySn") Long inquirySn);

    Optional<InquiryReportTarget> findInquiryReportTarget(@Param("prdCmtSn") Long prdCmtSn);
}
