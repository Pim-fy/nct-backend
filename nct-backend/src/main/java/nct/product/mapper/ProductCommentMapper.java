package nct.product.mapper;

import java.time.LocalDateTime;
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

    /** 문의 1건당 답변 1회 제한·수정 가능 시간(10분) 판단에 사용 */
    Optional<ProductComment> findReplyByParentSn(@Param("prdCmtParentSn") Long prdCmtParentSn);

    /** 답변 등록 후 10분 이내에만 호출되는 것을 전제로 내용만 갱신 */
    void updateReply(ProductComment comment);

    /** 문의 수정 — 답변이 없는 경우에만 UPDATE, 동시성 보호용 조건부 UPDATE */
    int updateInquiry(ProductComment comment);

    /** 쿨타임 체크 — usrSn+prdSn 기준 최신 문의(PRDC0006) 등록 시각 조회 */
    Optional<LocalDateTime> findLastInquiryTime(@Param("usrSn") Long usrSn, @Param("prdSn") Long prdSn);

    /** 백종남(6) 알림 클릭 이동용 — prdCmtSn → prdSn 변환 */
    Optional<Long> findProductSnByInquirySn(@Param("prdCmtSn") Long prdCmtSn);
}
