package nct.auth.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auth.domain.UserOauthLinkRow;

// @ai_generated: 작업단위5 - USER_OAUTH 연동 조회/저장 전용 Mapper.
/** USER_OAUTH(소셜 로그인 연동) 조회·저장·해제 */
@Mapper
public interface UserOauthMapper {

    /** provider+providerKey(UK_USER_OAUTH_PROVIDER_KEY) 기준 연동 회원 USR_SN 조회 */
    Optional<Long> findUsrSnByProviderAndKey(@Param("providerCd") String providerCd,
                                             @Param("providerKey") String providerKey);

    /** 신규 연동 저장 (일련번호·등록/갱신 일시·ID는 DDL DEFAULT로 채워짐) */
    void insert(@Param("usrSn") Long usrSn,
               @Param("providerCd") String providerCd,
               @Param("providerKey") String providerKey);

    // @ai_generated: 작업단위5 작업 2 - 마이페이지 연동 목록 조회용(읽기 전용, 잠금 없음)
    /** 특정 회원의 연동 목록 전체 조회 */
    List<UserOauthLinkRow> findByUsrSn(@Param("usrSn") Long usrSn);

    // @ai_generated: 레드팀 지적 반영 - 최소 1개 실제 로그인 수단(POL-AUTH-010) 검사용 TOCTOU 하드닝.
    // MemberMapper.findMemberByEmailForUpdate와 동일 패턴(SELECT ... FOR UPDATE). 서로 다른
    // provider를 동시에 해제하는 두 트랜잭션이 각자 "해제 전 개수"만 보고 통과해버리면 로그인
    // 수단이 0개로 계정이 잠길 수 있다 - 이 회원의 USER_OAUTH 행을 잠가 두 번째 트랜잭션이
    // 첫 번째 트랜잭션의 커밋(삭제 반영) 이후의 최신 개수로 재검사하게 만든다.
    /** 최소 로그인 수단 검사 전용 - 해당 회원의 연동 행을 잠가서 조회 */
    List<UserOauthLinkRow> findByUsrSnForUpdate(@Param("usrSn") Long usrSn);

    // @ai_generated: 작업단위5 작업 2 - 연동 해제. 영향받은 행 수(0 또는 1)를 반환해 존재 여부 판단에 사용
    /** 연동 해제 - 영향 행 수 반환 */
    int deleteByUsrSnAndProvider(@Param("usrSn") Long usrSn, @Param("providerCd") String providerCd);
}
