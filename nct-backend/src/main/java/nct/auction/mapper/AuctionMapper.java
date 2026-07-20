package nct.auction.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auction.dto.AuctionListItem;
import nct.auction.dto.AuctionListRequest;
import nct.auction.dto.AuctionBidItem;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.dto.AuctionImageItem;
import nct.auction.dto.AuctionStatusResponse;

@Mapper
public interface AuctionMapper {

    List<AuctionListItem> findAuctions(@Param("condition") AuctionListRequest condition);

    long countAuctions(@Param("condition") AuctionListRequest condition);

    AuctionDetailResponse findAuctionDetail(@Param("auctionId") Long auctionId);

    AuctionStatusResponse findAuctionStatusByProduct(@Param("prdSn") Long prdSn);

    List<AuctionImageItem> findAuctionImages(@Param("productId") Long productId);

    List<AuctionBidItem> findAuctionBids(@Param("auctionId") Long auctionId);

    AuctionBidTarget findAuctionBidTargetForUpdate(@Param("auctionId") Long auctionId);

    int incrementProductViewCount(@Param("auctionId") Long auctionId);

    int insertAuction(
            @Param("productId") Long productId,
            @Param("statusCode") String statusCode,
            @Param("currentAmount") BigDecimal currentAmount,
            @Param("bidUnitAmount") BigDecimal bidUnitAmount,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("actor") String actor);

    int updateCurrentHighestBids(@Param("auctionId") Long auctionId);

    int insertBid(
            @Param("auctionId") Long auctionId,
            @Param("userId") Long userId,
            @Param("bidAmount") java.math.BigDecimal bidAmount,
            @Param("statusCode") String statusCode,
            @Param("actor") String actor);

    int updateAuctionCurrentPrice(
            @Param("auctionId") Long auctionId,
            @Param("bidAmount") java.math.BigDecimal bidAmount,
            @Param("actor") String actor);

    int closeAuctionByInstantBuy(
            @Param("auctionId") Long auctionId,
            @Param("bidAmount") java.math.BigDecimal bidAmount,
            @Param("actor") String actor);
}
