package nct.auction.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auction.dto.MyBidHistoryItem;

@Mapper
public interface BidMapper {

    List<MyBidHistoryItem> findMyBidHistory(@Param("usrSn") Long usrSn);
}
