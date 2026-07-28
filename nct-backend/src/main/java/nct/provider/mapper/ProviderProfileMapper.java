package nct.provider.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.provider.dto.ProviderProfileResponse;

/** 담당자 7, F-PROV-004: PROVIDER_PROFILE만 다루는 MyBatis 계약이다. */
@Mapper
public interface ProviderProfileMapper {
    Optional<ProviderProfileResponse> findActiveByUserSn(@Param("userSn") Long userSn);
    int upsert(@Param("userSn") Long userSn, @Param("introduction") String introduction,
               @Param("availableArea") String availableArea, @Param("actorId") String actorId);
}
