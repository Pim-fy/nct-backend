package nct.member.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.member.domain.Member;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.dto.AdminMemberSource;
import nct.member.dto.PublicUserProfileSource;

@Mapper
public interface MemberMapper {

    Optional<Member> findMemberByEmail(String emailHmac);

    // @ai_generated: F-AUTH-011 TOCTOU 하드닝 - 정지 계정 탈퇴 확정 전용 잠금 조회.
    // SELECT ... FOR UPDATE로 해당 회원 행을 트랜잭션 종료까지 잠가, 상태 재검사(확인)와
    // withdraw() 실행(사용) 사이에 다른 트랜잭션이 상태를 바꿀 수 없게 한다. 활성 탈퇴 경로
    // (MemberService.withdrawActive)와 공유 withdraw()에는 영향 없음 - 이 경로에서만 사용.
    Optional<Member> findMemberByEmailForUpdate(String emailHmac);

    // @ai_generated: 로컬 로그인과 가입 중복 확인용 USERS 조회다.
    Optional<Member> findMemberByLoginId(String loginId);

    // @ai_generated: JWT subject(usrSn) 기반 조회 - 로그인 필터·재발급이 가변 필드(email) 대신 사용
    Optional<Member> findMemberById(Long usrSn);

    /** 담당자 7 통합 연결 · F-COM-008~009 지원: 거래 프로필에 허용된 회원 공개 필드만 조회한다. */
    Optional<PublicUserProfileSource> findPublicProfileById(@Param("usrSn") Long usrSn);

    /** 담당자 7 · F-OPS-002: 관리자 회원 상세/상태 변경의 행 잠금 조회입니다. */
    Optional<Member> findMemberByIdForUpdate(Long usrSn);

    /** 담당자 7 · F-OPS-002: 개인정보 원문을 제외한 관리자 회원 목록 계약입니다. */
    List<AdminMemberSource> findAdminMembers(
            @Param("statusCode") String statusCode,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("size") int size);

    Optional<AdminMemberSource> findAdminMemberById(@Param("usrSn") Long usrSn);

    /** 담당자 7 · F-OPS-002: 관리자 목록 조립용 비민감 회원 식별정보 일괄 조회입니다. */
    List<AdminMemberIdentityResponse> findAdminMemberIdentities(
            @Param("userSns") List<Long> userSns);

    long countAdminMembers(
            @Param("statusCode") String statusCode,
            @Param("keyword") String keyword);

    /** 담당자 7 · F-OPS-019: 상태를 조건부 변경하며 기존 리프레시 토큰을 즉시 폐기합니다. */
    int updateStatusAndInvalidateRefreshToken(
            @Param("usrSn") Long usrSn,
            @Param("expectedStatusCode") String expectedStatusCode,
            @Param("targetStatusCode") String targetStatusCode,
            @Param("updaterId") String updaterId);

    boolean existsByLoginId(String loginId);

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String emailHmac);

    void saveCertifiedMember(Member member);

    // @ai_generated: #{refreshTokenHash} 는 TokenHashUtil 로 해시화된 값(로그아웃 시 null) - USR_REFRESH_TOKEN_HASH 컬럼에 저장
    void updateRefreshTokenById(@Param("usrSn") Long usrSn,
                                @Param("refreshTokenHash") String refreshTokenHash);

    // @ai_generated: CHG-032/F-PROV-015 - 계정 자격이 아닌 현재 활성 접근 역할만 바꾼다.
    int updateRoleById(@Param("usrSn") Long usrSn,
                       @Param("role") String role);

    // @ai_generated: F-AUTH-007 - #{encodedPassword} 는 BCrypt 인코딩 완료 상태(PasswordResetService)
    void updatePasswordById(@Param("usrSn") Long usrSn,
                            @Param("encodedPassword") String encodedPassword);

    // @ai_generated: F-AUTH-010 - POL-AUTH-014(ISS-022 개정)로 확정된 6개 필드를 갱신한다.
    void updateProfile(@Param("usrSn") Long usrSn,
                       @Param("nickname") String nickname,
                       @Param("profileFileSn") Long profileFileSn,
                       @Param("emailCiphertext") String emailCiphertext,
                       @Param("emailHmac") String emailHmac,
                       @Param("bankNameCiphertext") String bankNameCiphertext,
                       @Param("accountNoCiphertext") String accountNoCiphertext,
                       @Param("clearBankAccount") boolean clearBankAccount,
                       @Param("telnoCiphertext") String telnoCiphertext,
                       @Param("zipCiphertext") String zipCiphertext,
                       @Param("clearZip") boolean clearZip,
                       @Param("addressCiphertext") String addressCiphertext,
                       @Param("clearAddress") boolean clearAddress,
                       @Param("addressDetailCiphertext") String addressDetailCiphertext,
                       @Param("clearAddressDetail") boolean clearAddressDetail);

    // @ai_generated: F-AUTH-011 - POL-AUTH-013 컬럼별 보존 범위를 한 트랜잭션으로 반영한다.
    void withdraw(@Param("usrSn") Long usrSn,
                 @Param("anonymizedEmailCiphertext") String anonymizedEmailCiphertext,
                 @Param("anonymizedEmailHmac") String anonymizedEmailHmac,
                 @Param("anonymizedNickname") String anonymizedNickname);
}
