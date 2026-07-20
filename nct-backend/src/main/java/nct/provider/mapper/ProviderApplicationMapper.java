package nct.provider.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.provider.domain.ProviderApplicationCommand;
import nct.provider.dto.ProviderApplicationResponse;

/** 담당자 7 · PROVIDER_APPLY/STATUS/PERMISSION 전용 MyBatis 계약입니다. */
@Mapper
public interface ProviderApplicationMapper {
    boolean isEmailCertified(@Param("userSn") Long userSn);
    List<ProviderApplicationResponse> findMine(@Param("userSn") Long userSn);
    List<ProviderApplicationResponse> findForAdmin(@Param("statusCode") String statusCode);
    Optional<ProviderApplicationResponse> findForUpdate(@Param("applicationSn") Long applicationSn);
    int countActivePending(@Param("userSn") Long userSn, @Param("categorySn") Long categorySn);
    int insertApplication(@Param("command") ProviderApplicationCommand command);
    int insertStatus(@Param("applicationSn") Long applicationSn, @Param("statusCode") String statusCode,
                     @Param("reason") String reason, @Param("actorId") String actorId);
    int changeApplicationStatus(@Param("applicationSn") Long applicationSn, @Param("statusCode") String statusCode,
                                @Param("reason") String reason, @Param("actorId") String actorId);
    int insertActivePermission(@Param("userSn") Long userSn, @Param("categorySn") Long categorySn,
                               @Param("applicationSn") Long applicationSn, @Param("actorId") String actorId);
    boolean hasActivePermission(@Param("userSn") Long userSn, @Param("categorySn") Long categorySn);
}
