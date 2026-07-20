package nct.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.member.domain.Member;
import nct.member.dto.ProfileUpdateRequest;
import nct.member.mapper.MemberMapper;

// @ai_generated
/** F-AUTH-010: 닉네임 변경시에만 중복 확인·DB 제약 위반 변환. F-AUTH-011: 비밀번호 재확인 후
 *  탈퇴 처리와 리프레시 토큰 무효화를 단위 검증한다. */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberMapper memberMapper;
    @Mock
    private AuthMemberPort authMemberPort;

    private PasswordEncoder passwordEncoder;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        memberService = new MemberService(memberMapper, authMemberPort, passwordEncoder);
    }

    @Test
    void 닉네임이_기존과_같으면_중복확인을_스킵한다() {
        when(memberMapper.findMemberById(101L)).thenReturn(Optional.of(memberWithNickname("구매자")));

        memberService.updateProfile(101L, profileRequest("구매자"));

        verify(memberMapper, never()).existsByNickname(anyString());
        verify(memberMapper).updateProfile(eq(101L), eq("구매자"), any(), anyString(), any(), any());
    }

    @Test
    void 닉네임이_바뀌고_이미_사용중이면_중복오류를_던진다() {
        when(memberMapper.findMemberById(101L)).thenReturn(Optional.of(memberWithNickname("구매자")));
        when(memberMapper.existsByNickname("새닉네임")).thenReturn(true);

        assertThatThrownBy(() -> memberService.updateProfile(101L, profileRequest("새닉네임")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);

        verify(memberMapper, never()).updateProfile(anyLong(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void 사전확인을_통과해도_DB_제약_위반이면_중복오류로_변환한다() {
        when(memberMapper.findMemberById(101L)).thenReturn(Optional.of(memberWithNickname("구매자")));
        when(memberMapper.existsByNickname("새닉네임")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("Duplicate entry for key 'UK_USERS_NM'"))
                .when(memberMapper).updateProfile(eq(101L), eq("새닉네임"), any(), anyString(), any(), any());

        assertThatThrownBy(() -> memberService.updateProfile(101L, profileRequest("새닉네임")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    void 비밀번호가_일치하면_탈퇴를_처리하고_세션을_무효화한다() {
        String encoded = passwordEncoder.encode("Password1!");
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(memberWithPassword(encoded)));

        memberService.withdrawActive(101L, "Password1!");

        verify(memberMapper).withdraw(eq(101L), anyString(), anyString());
        verify(authMemberPort).updateRefreshToken(101L, null);
    }

    @Test
    void 비밀번호가_틀리면_탈퇴를_차단한다() {
        String encoded = passwordEncoder.encode("Password1!");
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(memberWithPassword(encoded)));

        assertThatThrownBy(() -> memberService.withdrawActive(101L, "WrongPassword!"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(memberMapper, never()).withdraw(anyLong(), anyString(), anyString());
        verify(authMemberPort, never()).updateRefreshToken(anyLong(), isNull());
    }

    @Test
    void 공통_탈퇴_처리는_익명값으로_치환하고_리프레시토큰을_무효화한다() {
        memberService.withdraw(101L);

        verify(memberMapper).withdraw(eq(101L), eq("withdrawn_101@withdrawn.local"), eq("탈퇴한 사용자_101"));
        verify(authMemberPort).updateRefreshToken(101L, null);
    }

    private Member memberWithNickname(String nickname) {
        return Member.builder().usrSn(101L).usrNm(nickname).build();
    }

    private AuthMember memberWithPassword(String encodedPassword) {
        return AuthMember.builder()
                .id(101L).loginId("buyer01").email("user@example.com")
                .name("구매자").nickname("구매자").role("ROLE_USER").status("USRC0001")
                .password(encodedPassword).build();
    }

    private ProfileUpdateRequest profileRequest(String nickname) {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setNickname(nickname);
        request.setEmail("user@example.com");
        return request;
    }
}
