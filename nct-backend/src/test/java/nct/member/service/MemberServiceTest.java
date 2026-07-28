package nct.member.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.global.security.crypto.FieldCryptoService;
import nct.member.domain.Member;
import nct.member.dto.BuyerAddressSnapshot;
import nct.member.dto.PasswordChangeRequest;
import nct.member.dto.ProfileUpdateRequest;
import nct.member.dto.ProfileUpdateResponse;
import nct.member.mapper.MemberMapper;

// @ai_generated
/** F-AUTH-010: 닉네임 변경시에만 중복 확인·DB 제약 위반 변환. F-AUTH-011: 비밀번호 재확인 후
 *  탈퇴 처리와 리프레시 토큰 무효화를 단위 검증한다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberServiceTest {

    @Mock
    private MemberMapper memberMapper;
    @Mock
    private AuthMemberPort authMemberPort;
    @Mock
    private FieldCryptoService fieldCryptoService;
    @Mock
    private FileStorageService fileStorageService;

    private PasswordEncoder passwordEncoder;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        when(fieldCryptoService.encrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.decrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.emailHmac(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        memberService = new MemberService(memberMapper, authMemberPort, passwordEncoder, fieldCryptoService, fileStorageService);
    }

    @Test
    void 닉네임이_기존과_같으면_중복확인을_스킵한다() {
        when(memberMapper.findMemberById(101L)).thenReturn(Optional.of(memberWithNickname("구매자")));

        memberService.updateProfile(101L, profileRequest("구매자"));

        verify(memberMapper, never()).existsByNickname(anyString());
        verify(memberMapper).updateProfile(eq(101L), eq("구매자"), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 닉네임이_바뀌고_이미_사용중이면_중복오류를_던진다() {
        when(memberMapper.findMemberById(101L)).thenReturn(Optional.of(memberWithNickname("구매자")));
        when(memberMapper.existsByNickname("새닉네임")).thenReturn(true);

        assertThatThrownBy(() -> memberService.updateProfile(101L, profileRequest("새닉네임")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);

        verify(memberMapper, never()).updateProfile(anyLong(), anyString(), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 사전확인을_통과해도_DB_제약_위반이면_중복오류로_변환한다() {
        when(memberMapper.findMemberById(101L)).thenReturn(Optional.of(memberWithNickname("구매자")));
        when(memberMapper.existsByNickname("새닉네임")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("Duplicate entry for key 'UK_USERS_NM'"))
                .when(memberMapper).updateProfile(eq(101L), eq("새닉네임"), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any());

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

        verify(memberMapper).withdraw(eq(101L), anyString(), anyString(), anyString());
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

        verify(memberMapper, never()).withdraw(anyLong(), anyString(), anyString(), anyString());
        verify(authMemberPort, never()).updateRefreshToken(anyLong(), isNull());
    }

    @Test
    void 공통_탈퇴_처리는_익명값으로_치환하고_리프레시토큰을_무효화한다() {
        memberService.withdraw(101L);

        verify(memberMapper).withdraw(eq(101L), eq("withdrawn_101@withdrawn.local"),
                eq("withdrawn_101@withdrawn.local"), eq("탈퇴한 사용자_101"));
        verify(authMemberPort).updateRefreshToken(101L, null);
    }

    @Test
    void 구매자가_존재하지_않으면_USER_NOT_FOUND를_던진다() {
        when(memberMapper.findMemberById(101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getBuyerAddressSnapshot(101L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 구매자_주소가_불완전하면_BUYER_ADDRESS_INCOMPLETE를_던진다() {
        when(memberMapper.findMemberById(101L))
                .thenReturn(Optional.of(memberWithAddress("12345", "서울시 강남구", "  ")));

        assertThatThrownBy(() -> memberService.getBuyerAddressSnapshot(101L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.BUYER_ADDRESS_INCOMPLETE);
    }

    @Test
    void 구매자_주소가_모두_있으면_스냅샷을_반환한다() {
        when(memberMapper.findMemberById(101L))
                .thenReturn(Optional.of(memberWithAddress("12345", "서울시 강남구", "101동 202호")));

        BuyerAddressSnapshot snapshot = memberService.getBuyerAddressSnapshot(101L);

        assertThat(snapshot).isEqualTo(new BuyerAddressSnapshot("12345", "서울시 강남구", "101동 202호"));
    }

    @Test
    void 프로필_조회는_복호화된_값과_프로필사진_URL을_함께_반환한다() {
        Member member = Member.builder()
                .usrSn(101L).usrNm("구매자").usrPrflFlSn("55")
                .usrEml("user@example.com").usrBankNm("국민은행").usrAcntNo("123-456")
                .usrTelno("01012345678").usrZip("12345").usrAddr("서울시 강남구").usrDaddr("101동")
                .build();
        when(memberMapper.findMemberById(101L)).thenReturn(Optional.of(member));
        when(fileStorageService.getUrl(55L)).thenReturn("/api/attachment/profile/20260727/uuid.png");

        ProfileUpdateResponse response = memberService.getProfile(101L);

        assertThat(response.getNickname()).isEqualTo("구매자");
        assertThat(response.getProfileFileSn()).isEqualTo(55L);
        assertThat(response.getProfileImageUrl()).isEqualTo("/api/attachment/profile/20260727/uuid.png");
        assertThat(response.getPhone()).isEqualTo("01012345678");
        assertThat(response.getAddress()).isEqualTo("서울시 강남구");
    }

    @Test
    void 존재하지_않는_회원의_프로필_조회는_USER_NOT_FOUND를_던진다() {
        when(memberMapper.findMemberById(101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getProfile(101L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 현재_비밀번호가_맞고_새_비밀번호가_일치하면_변경하고_세션을_무효화한다() {
        String encoded = passwordEncoder.encode("Password1!");
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(memberWithPassword(encoded)));

        memberService.changePassword(101L, passwordChangeRequest("Password1!", "NewPassword2!", "NewPassword2!"));

        verify(authMemberPort).updatePassword(eq(101L), anyString());
        verify(authMemberPort).updateRefreshToken(101L, null);
    }

    @Test
    void 현재_비밀번호가_틀리면_비밀번호_변경을_차단한다() {
        String encoded = passwordEncoder.encode("Password1!");
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(memberWithPassword(encoded)));

        assertThatThrownBy(() -> memberService.changePassword(101L,
                passwordChangeRequest("WrongPassword!", "NewPassword2!", "NewPassword2!")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(authMemberPort, never()).updatePassword(anyLong(), anyString());
        verify(authMemberPort, never()).updateRefreshToken(anyLong(), isNull());
    }

    @Test
    void 새_비밀번호_확인이_다르면_비밀번호_변경을_차단한다() {
        String encoded = passwordEncoder.encode("Password1!");
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(memberWithPassword(encoded)));

        assertThatThrownBy(() -> memberService.changePassword(101L,
                passwordChangeRequest("Password1!", "NewPassword2!", "Mismatch3!")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

        verify(authMemberPort, never()).updatePassword(anyLong(), anyString());
        verify(authMemberPort, never()).updateRefreshToken(anyLong(), isNull());
    }

    @Test
    void 소셜_전용_계정은_비밀번호_변경을_차단한다() {
        AuthMember member = AuthMember.builder()
                .id(101L).loginId("OAUTH_01J000EXAMPLE").email("user@example.com")
                .name("구매자").nickname("구매자").role("ROLE_USER").status("USRC0001")
                .password(passwordEncoder.encode("SystemGenerated1!")).build();
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.changePassword(101L,
                passwordChangeRequest("아무값", "NewPassword2!", "NewPassword2!")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_CHANGE_NOT_SUPPORTED);

        verify(authMemberPort, never()).updatePassword(anyLong(), anyString());
        verify(authMemberPort, never()).updateRefreshToken(anyLong(), isNull());
    }

    @Test
    void 소셜_전용_계정의_프로필_조회는_passwordChangeable이_false다() {
        Member member = Member.builder().usrSn(101L).usrNm("구매자").usrLoginId("OAUTH_01J000EXAMPLE").build();
        when(memberMapper.findMemberById(101L)).thenReturn(Optional.of(member));

        ProfileUpdateResponse response = memberService.getProfile(101L);

        assertThat(response.isPasswordChangeable()).isFalse();
    }

    @Test
    void 로컬_가입_계정의_프로필_조회는_passwordChangeable이_true다() {
        Member member = Member.builder().usrSn(101L).usrNm("구매자").usrLoginId("buyer01").build();
        when(memberMapper.findMemberById(101L)).thenReturn(Optional.of(member));

        ProfileUpdateResponse response = memberService.getProfile(101L);

        assertThat(response.isPasswordChangeable()).isTrue();
    }

    private Member memberWithAddress(String zip, String addr, String daddr) {
        return Member.builder().usrSn(101L).usrZip(zip).usrAddr(addr).usrDaddr(daddr).build();
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

    private PasswordChangeRequest passwordChangeRequest(String current, String next, String confirm) {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        request.setNewPasswordConfirm(confirm);
        return request;
    }
}
