package nct.servicerequest.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.servicerequest.domain.SvcReqAddress;

/** 담당자 7: F-SVC-002 암호화 주소 저장·소유자 조회 Mapper. */
@Mapper
public interface SvcReqAddressMapper {

    void deleteBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    void insertAll(@Param("addresses") List<SvcReqAddress> addresses);

    List<SvcReqAddress> findBySvcReqSn(@Param("svcReqSn") Long svcReqSn);
}
