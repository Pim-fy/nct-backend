package nct.auction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auction.dto.AuctionCancelRequestRecord;
import nct.auction.dto.AuctionCancellationTarget;

@Mapper
public interface AuctionCancelRequestMapper {

    AuctionCancellationTarget findAuctionCancellationTargetForUpdate(@Param("auctionId") Long auctionId);

    AuctionCancellationTarget findAuctionCancellationTargetByCancelRequestForUpdate(
            @Param("cancelRequestSn") Long cancelRequestSn);

    AuctionCancelRequestRecord findCancelRequestForUpdate(@Param("cancelRequestSn") Long cancelRequestSn);

    int countPendingByAuction(@Param("auctionId") Long auctionId);

    int insertCancelRequest(
            @Param("auctionId") Long auctionId,
            @Param("requesterUserId") Long requesterUserId,
            @Param("reason") String reason,
            @Param("actor") String actor);

    int updateAuctionStatus(
            @Param("auctionId") Long auctionId,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("nextStatusCode") String nextStatusCode,
            @Param("actor") String actor);

    int approveCancelRequest(
            @Param("cancelRequestSn") Long cancelRequestSn,
            @Param("adminUserId") Long adminUserId,
            @Param("processReason") String processReason,
            @Param("actor") String actor);

    int rejectCancelRequest(
            @Param("cancelRequestSn") Long cancelRequestSn,
            @Param("adminUserId") Long adminUserId,
            @Param("rejectReason") String rejectReason,
            @Param("actor") String actor);

    int updateBidStatus(
            @Param("bidId") Long bidId,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("nextStatusCode") String nextStatusCode,
            @Param("actor") String actor);
}
