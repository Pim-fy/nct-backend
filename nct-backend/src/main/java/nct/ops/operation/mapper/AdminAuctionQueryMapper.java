package nct.ops.operation.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.ops.operation.dto.AdminAuctionListItemResponse;
import nct.ops.operation.dto.AdminAuctionListRequest;

/** 담당자 7 · F-OPS-003: 운영 화면 전용 읽기 Mapper입니다. */
@Mapper
public interface AdminAuctionQueryMapper {
    long count(@Param("condition") AdminAuctionListRequest condition);
    List<AdminAuctionListItemResponse> findPage(@Param("condition") AdminAuctionListRequest condition);
}
