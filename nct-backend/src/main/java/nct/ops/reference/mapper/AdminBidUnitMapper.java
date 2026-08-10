package nct.ops.reference.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.ops.reference.domain.CommonCode;

/** 담당자 7 · AUCG02 하위 입찰 단위를 관리하는 MyBatis 연결부입니다. */
@Mapper
public interface AdminBidUnitMapper {

    Optional<CommonCode> findGroupByCode(@Param("groupCode") String groupCode);

    Optional<CommonCode> findGroupByCodeForUpdate(@Param("groupCode") String groupCode);

    List<CommonCode> findAllByGroup(@Param("groupCode") String groupCode);

    List<CommonCode> findAllByGroupForUpdate(@Param("groupCode") String groupCode);

    Optional<CommonCode> findByIdAndGroupForUpdate(@Param("bidUnitSn") Long bidUnitSn,
                                                    @Param("groupCode") String groupCode);

    int countByName(@Param("groupCode") String groupCode,
                    @Param("name") String name,
                    @Param("excludeBidUnitSn") Long excludeBidUnitSn);

    int countActiveByGroup(@Param("groupCode") String groupCode);

    java.math.BigDecimal findMaxSortNoByGroup(@Param("groupCode") String groupCode);

    Integer findMaxCodeSequence(@Param("codePrefix") String codePrefix);

    int insert(@Param("code") CommonCode code, @Param("actorId") String actorId);

    int update(@Param("code") CommonCode code,
               @Param("groupCode") String groupCode,
               @Param("actorId") String actorId);

    int updateSortNo(@Param("bidUnitSn") Long bidUnitSn,
                     @Param("groupCode") String groupCode,
                     @Param("sortNo") java.math.BigDecimal sortNo,
                     @Param("actorId") String actorId);

    int updateUseYn(@Param("bidUnitSn") Long bidUnitSn,
                    @Param("groupCode") String groupCode,
                    @Param("useYn") String useYn,
                    @Param("actorId") String actorId);
}
