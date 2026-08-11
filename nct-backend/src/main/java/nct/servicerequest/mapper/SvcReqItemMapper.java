package nct.servicerequest.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.servicerequest.domain.SvcReqItem;
import nct.servicerequest.dto.ServiceRequestStoredAnswer;

@Mapper
public interface SvcReqItemMapper {

    void deleteBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    /** 재등록(마감된 요청서 복사) 전용 — 원본 행을 그대로 복제하기 위한 전체 컬럼 조회 */
    List<SvcReqItem> findItemEntitiesBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    List<String> findPublicItemContentsBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    List<String> findAllItemContentsBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    List<ServiceRequestStoredAnswer> findStructuredAnswersBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    void insertAll(@Param("items") List<SvcReqItem> items);
}
