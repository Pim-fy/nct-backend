package nct.ops.risk.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.ops.risk.domain.RiskEvent;
import nct.ops.risk.dto.AdminRiskEventListItemResponse;
import nct.ops.risk.dto.AdminRiskEventTypeSummaryResponse;

import java.util.List;

/**
 * RISK_EVENT 테이블을 조회·등록하는 MyBatis 연결부다.
 * SQL은 {@code resources/mapper/ops/risk/RiskEventMapper.xml}에서 확인할 수 있다.
 * 다른 담당자는 테이블을 직접 쓰지 않고 {@code RiskEventService}를 호출해야 한다.
 */
@Mapper
public interface RiskEventMapper {

    /** 같은 내용의 미처리 이벤트가 이미 있으면 기존 고유번호를 반환한다. */
    Long findUnprocessedDuplicateId(@Param("typeCode") String typeCode,
                                    @Param("referenceTypeCode") String referenceTypeCode,
                                    @Param("referenceSn") Long referenceSn,
                                    @Param("content") String content);

    /** 위험 이벤트 한 건을 등록하고, 성공하면 영향받은 행 수 1을 반환한다. */
    int insertRiskEvent(RiskEvent riskEvent);

    /** 담당자 7 · F-OPS-011: 관리자용 위험 이벤트 목록을 최신 등록순으로 조회한다. */
    List<AdminRiskEventListItemResponse> findAdminRiskEvents(
            @Param("typeCode") String typeCode, @Param("processedYn") String processedYn,
            @Param("offset") long offset, @Param("size") int size);

    long countAdminRiskEvents(@Param("typeCode") String typeCode,
                              @Param("processedYn") String processedYn);

    /** 대시보드가 재사용할 수 있는 위험 유형별 발생 건수다. */
    List<AdminRiskEventTypeSummaryResponse> countAdminRiskEventsByType(
            @Param("processedYn") String processedYn);
}
