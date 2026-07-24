package nct.auction.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auction.dto.AuctionListItem;
import nct.auction.dto.AuctionListRequest;
import nct.auction.dto.AuctionBidCreateCommand;
import nct.auction.dto.AuctionBidItem;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.dto.AuctionCancellationTarget;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.dto.AuctionImageItem;
import nct.auction.dto.AuctionStatusResponse;
import nct.auction.dto.AuctionStatusSummaryResponse;

@Mapper
public interface AuctionMapper {

    List<AuctionListItem> findAuctions(@Param("condition") AuctionListRequest condition);

    long countAuctions(@Param("condition") AuctionListRequest condition);

    Long findProductIdByAuctionId(@Param("auctionId") Long auctionId);

    AuctionDetailResponse findAuctionDetail(
            @Param("auctionId") Long auctionId,
            @Param("userId") Long userId);

    AuctionStatusResponse findAuctionStatusByProduct(@Param("prdSn") Long prdSn);

    List<AuctionStatusSummaryResponse> findAuctionStatusesByProducts(@Param("prdSns") List<Long> prdSns);

    List<AuctionImageItem> findAuctionImages(@Param("productId") Long productId);

    List<AuctionBidItem> findAuctionBids(@Param("auctionId") Long auctionId);

    AuctionBidTarget findAuctionBidTargetForUpdate(@Param("auctionId") Long auctionId);

    AuctionCancellationTarget findAuctionCancellationTargetForUpdate(@Param("auctionId") Long auctionId);

    List<Long> findExpiredActiveAuctionIds(@Param("limit") int limit);

    int insertAuction(
            @Param("productId") Long productId,
            @Param("statusCode") String statusCode,
            @Param("currentAmount") BigDecimal currentAmount,
            @Param("bidUnitAmount") BigDecimal bidUnitAmount,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("actor") String actor);

    int updateCurrentHighestBids(@Param("auctionId") Long auctionId);

    int exceptionCancelHighestBid(
            @Param("auctionId") Long auctionId,
            @Param("bidId") Long bidId,
            @Param("actor") String actor);

    int insertBid(AuctionBidCreateCommand command);

    int updateAuctionCurrentPrice(
            @Param("auctionId") Long auctionId,
            @Param("bidAmount") java.math.BigDecimal bidAmount,
            @Param("actor") String actor);

    int extendAuctionTime(
            @Param("auctionId") Long auctionId,
            @Param("extensionMinutes") int extensionMinutes,
            @Param("maxExtensionCount") int maxExtensionCount,
            @Param("actor") String actor);

    int closeAuctionByInstantBuy(
            @Param("auctionId") Long auctionId,
            @Param("bidAmount") java.math.BigDecimal bidAmount,
            @Param("actor") String actor);

    int updateExpiredAuctionStatus(
            @Param("auctionId") Long auctionId,
            @Param("statusCode") String statusCode,
            @Param("actor") String actor);

    int updateAuctionStatusForCancellation(
            @Param("auctionId") Long auctionId,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("newStatusCode") String newStatusCode,
            @Param("actor") String actor);
}
