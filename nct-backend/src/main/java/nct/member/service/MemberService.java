package nct.member.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.crypto.FieldCryptoService;
import nct.member.domain.Member;
import nct.file.service.FileStorageService;
import nct.member.dto.BuyerAddressSnapshot;
import nct.member.dto.PasswordChangeRequest;
import nct.member.dto.ProfileUpdateRequest;
import nct.member.dto.ProfileUpdateResponse;
import nct.member.mapper.MemberMapper;

// @ai_generated
/**
 * F-AUTH-010: 프로필 기본 정보 수정.
 * F-AUTH-011: 회원 탈퇴 공통 처리(활성/정지 두 경로가 이 클래스의 withdraw만 공유하고,
 * 각 경로의 본인확인 방식은 호출자가 각자 책임진다 - 활성은 여기서 비밀번호 재확인까지 겸한다).
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private static final String WITHDRAWN_EMAIL_DOMAIN = "@withdrawn.local";
    // @ai_generated: POL-AUTH-010 - 시스템 생성 로그인ID 예약 접두어(AuthService.normalizeLoginId·
    // MemberOauthLinkService.SYSTEM_LOGIN_ID_PREFIX와 동일 기준).
    private static final String SYSTEM_LOGIN_ID_PREFIX = "OAUTH_";

    private final MemberMapper memberMapper;
    private final AuthMemberPort authMemberPort;
    private final PasswordEncoder passwordEncoder;
    // @ai_generated: USERS 암호문은 이 서비스에서만 복호화해 응답 DTO 또는 도메인 계약으로 전달한다.
    private final FieldCryptoService fieldCryptoService;
    // @ai_generated: ISS-022 - 프로필사진 flSn을 화면에 그릴 URL로 바꿀 때만 사용(FILES 직접 조회 금지 원칙 준수).
    private final FileStorageService fileStorageService;

    /** F-AUTH-010: 닉네임이 기존과 다를 때만 중복 확인하고, DB 제약 위반도 최종 방어선으로 대비한다. */
    @Transactional
    public ProfileUpdateResponse updateProfile(Long usrSn, ProfileUpdateRequest request) {
        Member member = memberMapper.findMemberById(usrSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String nickname = request.getNickname().trim();
        if (!nickname.equals(member.getUsrNm()) && memberMapper.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
        // @ai_generated: ISS-023 - 전화번호가 필수로 전환돼 COALESCE 없이 직접 갱신하므로, DTO
        // @NotBlank를 우회해 이 메서드가 호출되더라도 빈 값으로 기존 전화번호를 지우지 않도록 재확인한다.
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            memberMapper.updateProfile(usrSn, nickname, request.getProfileFileSn(),
                    fieldCryptoService.encrypt(request.getEmail()),
                    fieldCryptoService.emailHmac(request.getEmail()),
                    fieldCryptoService.encrypt(request.getBankName()),
                    fieldCryptoService.encrypt(request.getAccountNo()),
                    fieldCryptoService.encrypt(request.getPhone()),
                    fieldCryptoService.encrypt(request.getZip()),
                    fieldCryptoService.encrypt(request.getAddress()),
                    fieldCryptoService.encrypt(request.getAddressDetail()));
        } catch (DataIntegrityViolationException ex) {
            throw duplicateException(ex);
        }

        // @ai_generated: profileFileSn/bankName/accountNo/phone/zip/address/addressDetail은 COALESCE로
        // 갱신돼 요청 DTO를 그대로 echo하면 "값을 안 보내 기존 값이 유지된" 경우를 null로 잘못
        // 보고하게 된다 - 갱신 후 실제 DB 상태를 다시 조회해 응답한다.
        return getProfile(usrSn);
    }

    /** ISS-022: 마이페이지 프로필 수정 화면이 현재 저장된 값을 표시할 조회 전용 API. */
    public ProfileUpdateResponse getProfile(Long usrSn) {
        Member member = memberMapper.findMemberById(usrSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Long profileFileSn = parseProfileFileSn(member.getUsrPrflFlSn());

        return ProfileUpdateResponse.builder()
                                    .nickname(member.getUsrNm())
                                    .profileFileSn(profileFileSn)
                                    .profileImageUrl(fileStorageService.getUrl(profileFileSn))
                                    .email(fieldCryptoService.decrypt(member.getUsrEml()))
                                    .bankName(fieldCryptoService.decrypt(member.getUsrBankNm()))
                                    .accountNo(fieldCryptoService.decrypt(member.getUsrAcntNo()))
                                    .phone(fieldCryptoService.decrypt(member.getUsrTelno()))
                                    .zip(fieldCryptoService.decrypt(member.getUsrZip()))
                                    .address(fieldCryptoService.decrypt(member.getUsrAddr()))
                                    .addressDetail(fieldCryptoService.decrypt(member.getUsrDaddr()))
                                    // ISS-022: 소셜 전용 계정은 비밀번호 변경 UI 자체를 화면에서 숨길 수 있게 알려준다.
                                    .passwordChangeable(!isSystemGeneratedLoginId(member.getUsrLoginId()))
                                    .build();
    }

    /** POL-AUTH-010 - MemberAuthAdapter가 생성한 "OAUTH_" 접두 로그인ID는 실제 비밀번호가 없는 계정이다. */
    private boolean isSystemGeneratedLoginId(String loginId) {
        return loginId != null && loginId.toUpperCase(Locale.ROOT).startsWith(SYSTEM_LOGIN_ID_PREFIX);
    }

    // @ai_generated: Member.usrPrflFlSn은 기존 도메인 클래스에서 String으로 선언돼 있어(DDL은 BIGINT)
    // 이 서비스 안에서만 로컬로 변환한다 - 팀원/기존 코드가 넓게 참조하는 Member.java 자체는 건드리지 않는다.
    private Long parseProfileFileSn(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    /**
     * ISS-022: 로그인 상태 비밀번호 변경 - 현재 비밀번호 재확인 후 즉시 처리.
     * withdrawActive와 동일하게 세션 자체가 본인확인을 증명하므로 이메일 왕복이 필요 없다.
     * 성공 시 리프레시 토큰을 무효화해 다른 기기·현재 세션 모두 재로그인하도록 강제한다
     * (PasswordResetService.confirmReset과 동일 패턴 - 비밀번호가 바뀌면 기존 토큰은 신뢰할 수 없다).
     */
    @Transactional
    public void changePassword(Long usrSn, PasswordChangeRequest request) {
        AuthMember member = authMemberPort.findById(usrSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // @ai_generated: 소셜 전용 계정은 currentPassword 일치 여부와 무관하게 아예 변경 불가 -
        // INVALID_CREDENTIALS로 두면 "비밀번호가 틀렸다"는 오해를 준다. 이 체크가 먼저다.
        if (isSystemGeneratedLoginId(member.getLoginId())) {
            throw new CustomException(ErrorCode.PASSWORD_CHANGE_NOT_SUPPORTED);
        }

        if (member.getPassword() == null || !passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        authMemberPort.updatePassword(usrSn, passwordEncoder.encode(request.getNewPassword()));
        authMemberPort.updateRefreshToken(usrSn, null);
    }

    /** F-AUTH-011: 활성 계정 탈퇴 - 현재 비밀번호 재확인 후 즉시 처리(이메일 링크 왕복 불필요). */
    @Transactional
    public void withdrawActive(Long usrSn, String currentPassword) {
        AuthMember member = authMemberPort.findById(usrSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // @ai_generated: 저장된 해시와 대조하는 신원 재확인이라 AuthService.login과 동일하게
        // INVALID_CREDENTIALS(401)를 쓴다 - PASSWORD_MISMATCH(400)는 같은 요청 내 필드 매칭용이라 부적합.
        if (member.getPassword() == null || !passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        withdraw(usrSn);
    }

    /**
     * F-AUTH-011/POL-AUTH-013: 활성·정지 두 경로가 공유하는 공통 탈퇴 처리.
     * USR_STATUS_CD 전환 + 컬럼별 보존 범위 반영(MemberMapper.withdraw) + 리프레시 토큰 무효화를
     * 하나의 트랜잭션으로 묶는다.
     */
    @Transactional
    public void withdraw(Long usrSn) {
        String anonymizedEmail = anonymizedEmail(usrSn);
        memberMapper.withdraw(usrSn, fieldCryptoService.encrypt(anonymizedEmail),
                fieldCryptoService.emailHmac(anonymizedEmail), anonymizedNickname(usrSn));
        // @ai_generated: 전 기기 로그아웃 - AuthService.logout과 동일 패턴(null 저장)
        authMemberPort.updateRefreshToken(usrSn, null);
    }

    /**
     * F-AUC-024 지원: 택배 거래 생성 시 낙찰자(구매자) 주소 스냅샷을 조회한다.
     * 회원이 존재하지 않으면 USER_NOT_FOUND, 우편번호·기본주소 중 하나라도 비어 있으면
     * BUYER_ADDRESS_INCOMPLETE를 던진다 - 호출 측은 반환값을 그대로 TRADE_DELIVERY에 복사하면 된다.
     */
    public BuyerAddressSnapshot getBuyerAddressSnapshot(Long buyerUsrSn) {
        Member member = memberMapper.findMemberById(buyerUsrSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String zip = fieldCryptoService.decrypt(member.getUsrZip());
        String address = fieldCryptoService.decrypt(member.getUsrAddr());
        String detailAddress = fieldCryptoService.decrypt(member.getUsrDaddr());
        if (isBlank(zip) || isBlank(address)) {
            throw new CustomException(ErrorCode.BUYER_ADDRESS_INCOMPLETE);
        }

        return new BuyerAddressSnapshot(zip, address, detailAddress == null ? "" : detailAddress.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String anonymizedEmail(Long usrSn) {
        return "withdrawn_" + usrSn + WITHDRAWN_EMAIL_DOMAIN;
    }

    private String anonymizedNickname(Long usrSn) {
        return "탈퇴한 사용자_" + usrSn;
    }

    private CustomException duplicateException(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains("UK_USERS_NM")) {
            return new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }
        if (message.contains("UK_USERS_EML_HMAC")) {
            return new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        return new CustomException(ErrorCode.CONFLICT);
    }
}
