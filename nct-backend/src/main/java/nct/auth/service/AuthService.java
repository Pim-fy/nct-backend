package nct.auth.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nct.auth.dto.LoginRequest;
import nct.auth.dto.LoginResponse;
import nct.auth.dto.SignUpRequest;
import nct.auth.dto.AvailabilityResponse;
import nct.auth.dto.FindEmailRequest;
import nct.auth.dto.FindEmailResponse;
import nct.auth.mapper.UserAgreementMapper;
import nct.auth.util.AgreementValidator;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.port.LocalSignUpProfile;
import nct.global.security.provider.JwtTokenProvider;
import nct.global.utils.TokenHashUtil;
import nct.ops.sanction.port.SanctionStatusReader;
import nct.provider.service.ProviderApplicationService;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;

/**
 * [인증 서비스]
 * - 회원 도메인을 직접 알지 못하고 AuthMemberPort(포트)로만 접근
 *   : 다른 프로젝트 이식 시 포트 구현체만 바꾸면 이 클래스는 수정 불필요
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    // @ai_generated: USRG01(회원 상태) 코드값 - docs/260716_08_DB_기초데이터_v3.sql 기준
    private static final String STATUS_ACTIVE = "USRC0001";
    private static final String STATUS_SUSPENDED = "USRC0002";
    private static final String STATUS_WITHDRAWN = "USRC0003";
    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_SERVICE = "ROLE_SERVICE";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    // @ai_generated: F-AUTH-014 - 마스킹된 로그인ID 앞부분 노출 글자 수(목업 "hong****" 패턴 기준)
    private static final int MASK_VISIBLE_CHARS = 4;

    private final PasswordEncoder passwordEncoder;
    private final AuthMemberPort authMemberPort;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailVerificationService emailVerificationService;
    private final UserAgreementMapper userAgreementMapper;
    private final Validator validator;
    private final TokenHashUtil tokenHashUtil;
    private final ProviderApplicationService providerApplicationService;
    private final SanctionStatusReader sanctionStatusReader;

    /**
     * 회원가입
     * - @ai_generated: 인증·필수 약관·중복을 같은 트랜잭션에서 재검증한 뒤에만 저장
     */
    @Transactional
    public LoginResponse signUp(SignUpRequest request) {
        String loginId = normalizeLoginId(request.getLoginId());
        String nickname = requireText(request.getNickname(), ErrorCode.INVALID_INPUT_VALUE);
        String email = normalizeEmail(request.getEmail());
        String telno = normalizeOptionalText(request.getTelno());
        String address = normalizeOptionalText(request.getAddress());
        String detailAddress = normalizeOptionalText(request.getDetailAddress());
        String zip = normalizeOptionalText(request.getZip());
        String bankName = normalizeOptionalText(request.getBankName());
        String accountNo = normalizeOptionalText(request.getAccountNo());
        requireCompletePair(address, zip);
        requireCompletePair(bankName, accountNo);
        if (detailAddress != null && address == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        AgreementValidator.validateAgreementSet(request.getAgreements());
        ensureSignupIdentifiersAvailable(loginId, nickname, email);
        emailVerificationService.requireVerifiedSignup(request.getVerificationId(), email);

        try {
            AuthMember member = authMemberPort.registerLocalMember(
                    LocalSignUpProfile.builder()
                                      .loginId(loginId)
                                      .email(email)
                                      .encodedPassword(passwordEncoder.encode(request.getPassword()))
                                      .nickname(nickname)
                                      .telno(telno)
                                      .address(address)
                                      .detailAddress(detailAddress)
                                      .zip(zip)
                                      .bankName(bankName)
                                      .accountNo(accountNo)
                                      .build());

            userAgreementMapper.insertAll(AgreementValidator.toUserAgreements(member.getId(), request.getAgreements()));
            emailVerificationService.markSignupUsed(request.getVerificationId());
            return toLoginResponse(member);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateException(ex);
        }
    }

    // @ai_generated: 프론트 사전 확인 API는 UX용이며 최종 가입의 DB 제약 검증을 대체하지 않는다.
    @Transactional(readOnly = true)
    public AvailabilityResponse checkLoginId(String loginId) {
        return AvailabilityResponse.builder()
                                   .available(!authMemberPort.existsByLoginId(normalizeLoginId(loginId)))
                                   .build();
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkNickname(String nickname) {
        return AvailabilityResponse.builder()
                                   .available(!authMemberPort.existsByNickname(
                                           requireText(nickname, ErrorCode.INVALID_INPUT_VALUE)))
                                   .build();
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkEmail(String email) {
        return AvailabilityResponse.builder()
                                   .available(!authMemberPort.existsByEmail(normalizeEmail(email)))
                                   .build();
    }

    /**
     * F-AUTH-014: 아이디 찾기
     * - 이메일+이름이 모두 일치하고 활성 상태인 계정만 성공으로 처리한다.
     * - 이메일 불일치·이름 불일치·정지·탈퇴·미가입을 구분하지 않고 전부 동일한 USER_NOT_FOUND로 응답한다
     *   (계정 존재 여부 노출 방지 - login()의 INVALID_CREDENTIALS 통일과 동일한 설계).
     */
    @Transactional(readOnly = true)
    public FindEmailResponse findEmail(FindEmailRequest request) {
        String email = normalizeEmail(request.getEmail());
        String name = requireText(request.getName(), ErrorCode.INVALID_INPUT_VALUE);

        AuthMember member = authMemberPort.findByEmail(email).orElse(null);
        boolean matched = member != null
                && name.equals(member.getName())
                && STATUS_ACTIVE.equals(member.getStatus());
        if (!matched) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        return FindEmailResponse.builder()
                                .maskedLoginId(maskLoginId(member.getLoginId()))
                                .build();
    }

    // @ai_generated: 실제 길이를 노출하지 않도록 앞 N자 뒤에 항상 고정 4개의 '*'를 붙인다(목업 "hong****" 패턴).
    private String maskLoginId(String loginId) {
        int visible = Math.min(MASK_VISIBLE_CHARS, loginId.length());
        return loginId.substring(0, visible) + "****";
    }

    /**
     * 로그인
     * - 비밀번호 검증 -> JWT 발급 -> Refresh DB 저장
     * - 실패 사유(사용자 없음/비밀번호 불일치)를 구분하지 않고
     *   동일한 INVALID_CREDENTIALS 로 응답 (계정 존재 여부 노출 방지)
     */
    @Transactional
    // @ai_generated: 토큰·응답 모델만 반환해 Service가 HttpServlet API에 의존하지 않게 한다.
    public AuthSessionResult login(LoginRequest request) {
        AuthMember member = authMemberPort.findByLoginId(normalizeLoginId(request.getLoginId()))
                                          .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (member.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // @ai_generated: F-AUTH-009 - 정지/탈퇴 계정은 비밀번호가 맞아도 로그인을 차단한다.
        requireActiveStatus(member.getStatus());

        String accessToken  = jwtTokenProvider.createAccessToken(member.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        authMemberPort.updateRefreshToken(member.getId(), refreshToken);
        return AuthSessionResult.builder()
                                .loginResponse(toLoginResponse(member))
                                .accessToken(accessToken)
                                .refreshToken(refreshToken)
                                .build();
    }

    /**
     * Access Token 재발급
     * - Refresh 쿠키 -> JWT 검증 -> DB 저장값과 비교(탈취 토큰 재사용 방지) -> 새 Access 발급
     */
    @Transactional(readOnly = true)
    public String refresh(String refreshToken) {
        AuthMember member = verifyRefreshToken(refreshToken);
        return jwtTokenProvider.createAccessToken(member.getId());
    }

    /**
     * 새로고침 자동 로그인
     * - refresh 와 동일한 검증 후 사용자 정보까지 반환
     *   : 프론트엔드 전역 상태(Context) 복원용
     */
    @Transactional(readOnly = true)
    public AuthSessionResult verifyAndRefresh(String refreshToken) {
        AuthMember member = verifyRefreshToken(refreshToken);
        String accessToken = jwtTokenProvider.createAccessToken(member.getId());
        return AuthSessionResult.builder()
                                .loginResponse(toLoginResponse(member))
                                .accessToken(accessToken)
                                .build();
    }

    /**
     * 로그아웃
     * - DB Refresh Token 무효화 (쿠키 삭제는 Controller가 담당)
     */
    @Transactional
    public void logout(Long memberId) {
        authMemberPort.updateRefreshToken(memberId, null);
    }

    /**
     * @ai_generated CHG-032/F-PROV-015: 현재 활성 접근 역할을 DB에 반영하고 새 Access Token을 발급한다.
     * Refresh Token은 역할 권한 원천이 아니므로 기존 로그인 유지 세션을 보존한다.
     */
    @Transactional
    public AuthSessionResult switchMode(Long memberId, String target) {
        if (memberId == null || memberId <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        AuthMember member = authMemberPort.findById(memberId)
                                          .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        requireActiveStatus(member.getStatus());
        if (ROLE_ADMIN.equals(member.getRole())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        String targetRole = resolveTargetRole(target);
        if (ROLE_SERVICE.equals(targetRole)) {
            providerApplicationService.requireAnyActivePermission(member.getId());
            sanctionStatusReader.requireNoActiveSanction(member.getId());
        }

        if (authMemberPort.updateRole(member.getId(), targetRole) != 1) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return AuthSessionResult.builder()
                                .loginResponse(toLoginResponse(member, targetRole))
                                .accessToken(jwtTokenProvider.createAccessToken(member.getId()))
                                .build();
    }

    /** Refresh 쿠키 추출 -> JWT 검증 -> DB 저장 해시와 대조 */
    private AuthMember verifyRefreshToken(String refreshToken) {
        if (refreshToken == null) {
            throw new CustomException(ErrorCode.TOKEN_NOT_FOUND);
        }

        // 만료/위조 시 CustomException(EXPIRED_TOKEN/INVALID_TOKEN) 발생
        Long usrSn = jwtTokenProvider.getUsrSn(refreshToken);

        AuthMember member = authMemberPort.findById(usrSn)
                                          .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // @ai_generated: 요청 토큰을 동일하게 해시화한 뒤 DB 저장 해시와 대조 (JWT 서명·만료는 위 getUsrSn 단계에서 이미 검증됨)
        // 저장값이 null(로그아웃 상태)이거나 다르면 탈취/이전 토큰 -> 거부
        if (member.getRefreshToken() == null
                || !tokenHashUtil.hash(refreshToken).equals(member.getRefreshToken())) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        // @ai_generated: F-AUTH-009 - 재발급 시점에도 계정 상태를 재확인한다(로그인 이후 정지된 경우 대비).
        requireActiveStatus(member.getStatus());

        return member;
    }

    /** F-AUTH-009: 계정 상태가 활성(USRC0001)이 아니면 정지/탈퇴 예외를 던진다. */
    private void requireActiveStatus(String status) {
        if (STATUS_SUSPENDED.equals(status)) {
            throw new CustomException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (STATUS_WITHDRAWN.equals(status)) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
    }

    private String resolveTargetRole(String target) {
        if ("USER".equals(target)) {
            return ROLE_USER;
        }
        if ("SERVICE".equals(target)) {
            return ROLE_SERVICE;
        }
        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
    }

    private LoginResponse toLoginResponse(AuthMember member) {
        return toLoginResponse(member, member.getRole());
    }

    private LoginResponse toLoginResponse(AuthMember member, String role) {
        return LoginResponse.builder()
                            .id(member.getId())
                            .email(member.getEmail())
                            .name(member.getName())
                            .nickname(member.getNickname())
                             .role(role)
                            .provider(member.getProvider())
                            .build();
    }

    private void ensureSignupIdentifiersAvailable(String loginId, String nickname, String email) {
        if (authMemberPort.existsByLoginId(loginId)) {
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (authMemberPort.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
        if (authMemberPort.existsByEmail(email)) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    private String normalizeLoginId(String loginId) {
        String value = requireText(loginId, ErrorCode.INVALID_INPUT_VALUE);
        if (!value.matches("^[A-Za-z0-9._-]{4,50}$")) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (value.toUpperCase(Locale.ROOT).startsWith("OAUTH_")) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return value;
    }

    private String normalizeEmail(String email) {
        String normalizedEmail = requireText(email, ErrorCode.INVALID_INPUT_VALUE).toLowerCase(Locale.ROOT);
        if (!validator.validate(new EmailValue(normalizedEmail)).isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_EMAIL_FORMAT);
        }
        return normalizedEmail;
    }

    // @ai_generated: GET 중복확인도 가입 DTO의 @Email 규칙과 같은 Validator로 검사한다.
    private record EmailValue(@Email String email) {
    }

    private String requireText(String value, ErrorCode errorCode) {
        if (value == null || value.isBlank()) {
            throw new CustomException(errorCode);
        }
        return value.trim();
    }

    // @ai_generated: 선택 입력의 공백은 DB NULL로 통일하고, 주소·계좌 필드는 반쪽 저장을 차단한다.
    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireCompletePair(String firstValue, String secondValue) {
        if ((firstValue == null) != (secondValue == null)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private CustomException duplicateException(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains("UK_USERS_LOGIN_ID")) {
            return new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (message.contains("UK_USERS_NM")) {
            return new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
        if (message.contains("UK_USERS_EML")) {
            return new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        return new CustomException(ErrorCode.CONFLICT);
    }
}
