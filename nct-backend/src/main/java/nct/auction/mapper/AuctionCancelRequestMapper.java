package nct.auction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auction.dto.AuctionCancelRequestCreateCommand;
import nct.auction.dto.AuctionCancelRequestProcessTarget;
import nct.auction.dto.AuctionPendingCancelRequestResponse;

@Mapper
public interface AuctionCancelRequestMapper {

    boolean existsPendingByAuctionId(@Param("auctionId") Long auctionId);

    AuctionPendingCancelRequestResponse findPendingByAuctionId(
            @Param("auctionId") Long auctionId);

    int insertCancelRequest(AuctionCancelRequestCreateCommand command);

    AuctionCancelRequestProcessTarget findProcessTargetForUpdate(
            @Param("cancelRequestSn") Long cancelRequestSn);

    int processPendingRequest(
            @Param("cancelRequestSn") Long cancelRequestSn,
            @Param("approvalYn") String approvalYn,
            @Param("adminUserId") Long adminUserId,
            @Param("processReason") String processReason,
            @Param("actor") String actor);
}
