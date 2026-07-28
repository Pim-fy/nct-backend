package nct.servicerequest.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.servicerequest.domain.SvcReqItem;

@Mapper
public interface SvcReqItemMapper {

    void deleteBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    List<String> findItemContentsBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    void insertAll(@Param("items") List<SvcReqItem> items);
}
