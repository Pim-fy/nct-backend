package nct.auction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auction.dto.AuctionCancelRequestCreateCommand;

@Mapper
public interface AuctionCancelRequestMapper {

    boolean existsPendingByAuctionId(@Param("auctionId") Long auctionId);

    int insertCancelRequest(AuctionCancelRequestCreateCommand command);
}
