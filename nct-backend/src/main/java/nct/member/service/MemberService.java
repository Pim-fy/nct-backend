package nct.member.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.member.domain.Member;
import nct.member.dto.BuyerAddressSnapshot;
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

    private final MemberMapper memberMapper;
    private final AuthMemberPort authMemberPort;
    private final PasswordEncoder passwordEncoder;

    /** F-AUTH-010: 닉네임이 기존과 다를 때만 중복 확인하고, DB 제약 위반도 최종 방어선으로 대비한다. */
    @Transactional
    public ProfileUpdateResponse updateProfile(Long usrSn, ProfileUpdateRequest request) {
        Member member = memberMapper.findMemberById(usrSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String nickname = request.getNickname().trim();
        if (!nickname.equals(member.getUsrNm()) && memberMapper.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }

        try {
            memberMapper.updateProfile(usrSn, nickname, request.getProfileFileSn(),
                    request.getEmail(), request.getBankName(), request.getAccountNo());
        } catch (DataIntegrityViolationException ex) {
            throw duplicateException(ex);
        }

        // @ai_generated: profileFileSn/bankName/accountNo는 COALESCE로 갱신돼 요청 DTO를 그대로
        // echo하면 "값을 안 보내 기존 값이 유지된" 경우를 null로 잘못 보고하게 된다 - 갱신 후
        // 실제 DB 상태를 다시 조회해 응답한다.
        Member updated = memberMapper.findMemberById(usrSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return ProfileUpdateResponse.builder()
                                    .nickname(updated.getUsrNm())
                                    .profileFileSn(parseProfileFileSn(updated.getUsrPrflFlSn()))
                                    .email(updated.getUsrEml())
                                    .bankName(updated.getUsrBankNm())
                                    .accountNo(updated.getUsrAcntNo())
                                    .build();
    }

    // @ai_generated: Member.usrPrflFlSn은 기존 도메인 클래스에서 String으로 선언돼 있어(DDL은 BIGINT)
    // 이 서비스 안에서만 로컬로 변환한다 - 팀원/기존 코드가 넓게 참조하는 Member.java 자체는 건드리지 않는다.
    private Long parseProfileFileSn(String value) {
        return value == null ? null : Long.valueOf(value);
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
        memberMapper.withdraw(usrSn, anonymizedEmail(usrSn), anonymizedNickname(usrSn));
        // @ai_generated: 전 기기 로그아웃 - AuthService.logout과 동일 패턴(null 저장)
        authMemberPort.updateRefreshToken(usrSn, null);
    }

    /**
     * F-AUC-024 지원: 택배 거래 생성 시 낙찰자(구매자) 주소 스냅샷을 조회한다.
     * 회원이 존재하지 않으면 USER_NOT_FOUND, 주소 3필드 중 하나라도 비어 있으면
     * BUYER_ADDRESS_INCOMPLETE를 던진다 - 호출 측은 반환값을 그대로 TRADE_DELIVERY에 복사하면 된다.
     */
    public BuyerAddressSnapshot getBuyerAddressSnapshot(Long buyerUsrSn) {
        Member member = memberMapper.findMemberById(buyerUsrSn)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (isBlank(member.getUsrZip()) || isBlank(member.getUsrAddr()) || isBlank(member.getUsrDaddr())) {
            throw new CustomException(ErrorCode.BUYER_ADDRESS_INCOMPLETE);
        }

        return new BuyerAddressSnapshot(member.getUsrZip(), member.getUsrAddr(), member.getUsrDaddr());
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
        if (message.contains("UK_USERS_EML")) {
            return new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        return new CustomException(ErrorCode.CONFLICT);
    }
}
