package nct.settlement.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.settlement.domain.Settlement;

@Mapper
public interface SettlementMapper {

    int insert(Settlement settlement);

    Settlement selectForUpdate(@Param("stlmSn") long stlmSn);

    int updateStatus(@Param("stlmSn") long stlmSn, @Param("statusCd") String statusCd);

    List<Settlement> selectListByUser(@Param("usrSn") long usrSn);

    List<Settlement> selectListByTrade(@Param("trdSn") long trdSn);
}
