package nct.auction.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auction.dto.AuctionListItem;
import nct.auction.dto.AuctionListRequest;
import nct.auction.dto.AuctionBidItem;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.dto.AuctionDetailResponse;
import nct.auction.dto.AuctionImageItem;

@Mapper
public interface AuctionMapper {

    List<AuctionListItem> findAuctions(@Param("condition") AuctionListRequest condition);

    long countAuctions(@Param("condition") AuctionListRequest condition);

    AuctionDetailResponse findAuctionDetail(@Param("auctionId") Long auctionId);

    List<AuctionImageItem> findAuctionImages(@Param("productId") Long productId);

    List<AuctionBidItem> findAuctionBids(@Param("auctionId") Long auctionId);

    AuctionBidTarget findAuctionBidTargetForUpdate(@Param("auctionId") Long auctionId);

    int incrementProductViewCount(@Param("auctionId") Long auctionId);

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
