package nct.provider.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.provider.domain.PortfolioRecord;
import nct.provider.dto.PortfolioResponse;

/** 담당자 7, F-PROV-005: PORTFOLIO와 PORTFOLIO_FILE을 관리하는 MyBatis 계약이다. */
@Mapper
public interface PortfolioMapper {
    List<PortfolioResponse> findActiveByUserSn(@Param("userSn") Long userSn);

    Optional<PortfolioResponse> findActiveOwned(@Param("portfolioSn") Long portfolioSn,
            @Param("userSn") Long userSn);

    int insert(PortfolioRecord portfolio);

    int update(@Param("portfolioSn") Long portfolioSn, @Param("userSn") Long userSn,
            @Param("title") String title, @Param("content") String content,
            @Param("actorId") String actorId);

    int softDelete(@Param("portfolioSn") Long portfolioSn, @Param("userSn") Long userSn,
            @Param("actorId") String actorId);

    int deleteFiles(@Param("portfolioSn") Long portfolioSn);

    int insertFiles(@Param("portfolioSn") Long portfolioSn, @Param("fileIds") List<Long> fileIds,
            @Param("actorId") String actorId);
}
