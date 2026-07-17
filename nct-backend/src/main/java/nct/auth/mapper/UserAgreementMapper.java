package nct.auth.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auth.domain.UserAgreement;

// @ai_generated
/** 최종 가입 트랜잭션에서만 고정 약관 3건을 저장하는 Mapper다. */
@Mapper
public interface UserAgreementMapper {

    void insertAll(@Param("agreements") List<UserAgreement> agreements);
}
