package nct.servicerequest.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.servicerequest.domain.SvcReqItem;
import nct.servicerequest.dto.ServiceRequestStoredAnswer;

@Mapper
public interface SvcReqItemMapper {

    void deleteBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    List<String> findPublicItemContentsBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    List<String> findAllItemContentsBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    List<ServiceRequestStoredAnswer> findStructuredAnswersBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    void insertAll(@Param("items") List<SvcReqItem> items);
}
