package nct.ops.reference.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.ops.reference.domain.CommonCode;

/**
 * CMM_CODE 테이블의 활성 코드만 읽는 MyBatis 연결부다.
 * SQL은 {@code resources/mapper/ops/reference/CommonCodeMapper.xml}에 있다.
 */
@Mapper
public interface CommonCodeMapper {

    /** 지정한 그룹 안에 해당 코드가 활성 상태로 존재하는지 한 건 조회한다. */
    Optional<CommonCode> findActiveByGroupAndCode(@Param("groupCode") String groupCode,
                                                   @Param("code") String code);

    /** 지정한 그룹에 속한 활성 코드들을 표시 순서대로 조회한다. */
    List<CommonCode> findActiveChildrenByGroup(@Param("groupCode") String groupCode);
}
