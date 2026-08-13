package nct.quote.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.quote.domain.Quote;
import nct.quote.domain.QuoteHistory;
import nct.quote.domain.QuotePhoto;
import nct.quote.dto.AdminQuoteListItem;
import nct.quote.dto.AdminQuoteSummary;
import nct.quote.dto.MyQuoteSummaryCounts;
import nct.quote.dto.QuoteAttachmentResponse;
import nct.quote.dto.QuoteHistoryResponse;
import nct.quote.dto.QuoteResponse;
import nct.quote.dto.QuoteUpdateRequest;
import nct.quote.dto.QuoteSanctionTarget;
import nct.quote.dto.ReceivedQuoteResponse;

@Mapper
public interface QuoteMapper {

    int insertQuote(Quote quote);

    /** 잠금 없는 단건 조회 — 소유권 확인, 이력 조회 등 읽기 전용 용도 */
    Quote findQuoteById(@Param("qutSn") Long qutSn);

    /** FOR UPDATE 단건 조회 — 수정·철회 시 동시성 제어 전용 */
    Quote findQuoteByIdForUpdate(@Param("qutSn") Long qutSn);

    int updateQuote(
            @Param("qutSn") Long qutSn,
            @Param("req") QuoteUpdateRequest req,
            @Param("updtId") String updtId);

    int withdrawQuote(
            @Param("qutSn") Long qutSn,
            @Param("updtId") String updtId);

    int adminInvalidateActiveQuote(
            @Param("qutSn") Long qutSn,
            @Param("updtId") String updtId);

    List<Quote> findActiveQuotesByServiceRequestIdForUpdate(
            @Param("svcReqSn") Long svcReqSn);

    int adminInvalidateActiveQuotes(
            @Param("svcReqSn") Long svcReqSn,
            @Param("updtId") String updtId);

    List<QuoteSanctionTarget> findSanctionTargetsByMemberForUpdate(
            @Param("userSn") Long userSn);

    int withdrawQuoteForSanction(
            @Param("quoteId") Long quoteId,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("actorId") String actorId);

    int withdrawSelectedQuoteAfterTradeCancellation(
            @Param("quoteId") Long quoteId,
            @Param("actorId") String actorId);

    List<QuoteResponse> findMyQuotes(
            @Param("usrSn") Long usrSn,
            @Param("offset") int offset,
            @Param("size") int size);

    QuoteResponse findMyQuote(
            @Param("usrSn") Long usrSn,
            @Param("qutSn") Long qutSn);

    int countMyQuotes(@Param("usrSn") Long usrSn);

    /** 담당자 7 · F-PROV-009: 제공자 견적을 전체·활성·선택·종료 상태로 한 번에 집계합니다. */
    MyQuoteSummaryCounts findMyQuoteSummary(@Param("usrSn") Long usrSn);

    List<AdminQuoteSummary> findAdminSummaries(
            @Param("serviceRequestIds") List<Long> serviceRequestIds);

    List<AdminQuoteListItem> findAdminQuoteListItems(
            @Param("serviceRequestId") Long serviceRequestId);

    QuoteResponse findMyActiveQuote(
            @Param("usrSn") Long usrSn,
            @Param("svcReqSn") Long svcReqSn);

    int insertQuoteHistory(QuoteHistory history);

    List<QuoteHistoryResponse> findQuoteHistory(@Param("qutSn") Long qutSn);

    List<ReceivedQuoteResponse> findQuotesBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    int insertQuotePhoto(QuotePhoto quotePhoto);

    int deleteQuotePhotosByQutSn(@Param("qutSn") Long qutSn);

    List<QuoteAttachmentResponse> findQuoteAttachments(@Param("qutSn") Long qutSn);

    int countQuoteAttachment(
            @Param("qutSn") Long qutSn,
            @Param("flSn") Long flSn);

    /** 견적 선택 상태 전이 — 서비스 레이어에서 FOR UPDATE 잠금 선행 필수 (F-SVC-009) */
    int selectQuote(@Param("qutSn") Long qutSn, @Param("updtId") String updtId);

    /** 견적 선택 시 같은 서비스 요청의 경쟁 견적 일괄 철회 (F-SVC-009) */
    int withdrawCompetingQuotes(
            @Param("svcReqSn") Long svcReqSn,
            @Param("excludeQutSn") Long excludeQutSn,
            @Param("updtId") String updtId);

    // @ai_generated (담당자1 황희준, 2026-08-12, 조율 대기): F-AUTH-011/POL-AUTH-013 - 회원 탈퇴 시
    // 본인(제공자)이 제출한 진행 중 견적을 전부 철회 처리한다. 영향 행 수를 반환한다.
    int withdrawAllByUser(
            @Param("usrSn") Long usrSn,
            @Param("updtId") String updtId);

    /** 담당자2 소비: 해당 서비스 요청에 견적을 제출한 제공자 USR_SN distinct 목록 (철회 포함) */
    List<Long> findProviderUsrSnBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    /** 담당자2 소비: 서비스 요청 마감 시 미선택 활성 견적 제공자 USR_SN 목록 (QUTC0001/0002만) */
    List<Long> findActiveQuoteProvidersBySvcReqSn(@Param("svcReqSn") Long svcReqSn);
}
