package nct.quote.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.quote.domain.Quote;
import nct.quote.domain.QuoteHistory;
import nct.quote.dto.QuoteHistoryResponse;
import nct.quote.dto.QuoteResponse;
import nct.quote.dto.QuoteUpdateRequest;

@Mapper
public interface QuoteMapper {

    int insertQuote(Quote quote);

    Quote findQuoteByIdForUpdate(@Param("qutSn") Long qutSn);

    // SERVICE_REQUEST.USR_SN 직접 조회 — 담당자2(신현석) 서비스 요청 계약 완성 시 교체 예정
    Long findRequesterUsrSn(@Param("svcReqSn") Long svcReqSn);

    int updateQuote(
            @Param("qutSn") Long qutSn,
            @Param("req") QuoteUpdateRequest req,
            @Param("updtId") String updtId);

    int withdrawQuote(
            @Param("qutSn") Long qutSn,
            @Param("updtId") String updtId);

    List<QuoteResponse> findMyQuotes(
            @Param("usrSn") Long usrSn,
            @Param("offset") int offset,
            @Param("size") int size);

    int countMyQuotes(@Param("usrSn") Long usrSn);

    int insertQuoteHistory(QuoteHistory history);

    List<QuoteHistoryResponse> findQuoteHistory(@Param("qutSn") Long qutSn);
}
