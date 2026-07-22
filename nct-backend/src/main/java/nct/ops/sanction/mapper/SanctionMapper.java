package nct.ops.sanction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** @ai_generated F-AUTH-012 SANCTION의 현재 유효 제재 여부만 제공하는 담당자5 읽기 Mapper다. */
@Mapper
public interface SanctionMapper {

    /** 시작 시각이 지났고 종료되지 않은 제재가 하나라도 있으면 true다. */
    boolean existsActiveSanction(@Param("userSn") Long userSn);
}
