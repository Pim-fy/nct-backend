package nct.quote.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.quote.domain.Quote;
import nct.quote.domain.QuoteHistory;
import nct.quote.dto.QuoteHistoryResponse;
import nct.quote.dto.QuoteResponse;
import nct.quote.dto.QuoteUpdateRequest;
import nct.quote.dto.ReceivedQuoteResponse;

@Mapper
public interface QuoteMapper {

    int insertQuote(Quote quote);

    Quote findQuoteByIdForUpdate(@Param("qutSn") Long qutSn);

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

    List<ReceivedQuoteResponse> findQuotesBySvcReqSn(@Param("svcReqSn") Long svcReqSn);
}
