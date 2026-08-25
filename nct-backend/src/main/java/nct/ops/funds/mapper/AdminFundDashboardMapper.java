package nct.ops.funds.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.ops.funds.dto.AdminFundDailyFlowResponse;
import nct.ops.funds.dto.AdminFundSnapshot;

@Mapper
public interface AdminFundDashboardMapper {

    AdminFundSnapshot findSnapshot();

    List<AdminFundDailyFlowResponse> findDailyFlows(
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);
}
