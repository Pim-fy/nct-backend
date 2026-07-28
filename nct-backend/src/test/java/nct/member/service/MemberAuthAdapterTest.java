package nct.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import nct.auth.mapper.UserOauthMapper;
import nct.global.security.crypto.FieldCryptoService;
import nct.global.security.port.OAuthProfile;
import nct.global.utils.TokenHashUtil;
import nct.member.domain.Member;
import nct.member.mapper.MemberMapper;

// @ai_generated
/** OAuth 선택 개인정보가 평문이 아닌 암호문으로 USERS Mapper에 전달되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class MemberAuthAdapterTest {

    @Mock
    private MemberMapper memberMapper;
    @Mock
    private TokenHashUtil tokenHashUtil;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserOauthMapper userOauthMapper;
    @Mock
    private FieldCryptoService fieldCryptoService;

    private MemberAuthAdapter memberAuthAdapter;

    @BeforeEach
    void setUp() {
        memberAuthAdapter = new MemberAuthAdapter(
                memberMapper, tokenHashUtil, passwordEncoder, userOauthMapper, fieldCryptoService);
        when(passwordEncoder.encode(any())).thenReturn("encoded-system-password");
        when(fieldCryptoService.encrypt("oauth@example.com")).thenReturn("enc-email");
        when(fieldCryptoService.emailHmac("oauth@example.com")).thenReturn("email-hmac");
        when(fieldCryptoService.providerKeyHmac("USRC0004", "provider-user-1")).thenReturn("provider-key-hmac");
        when(fieldCryptoService.decrypt("enc-email")).thenReturn("oauth@example.com");
    }

    @Test
    void OAuth_선택정보_다섯개를_암호화한_값으로_Mapper에_전달한다() {
        OAuthProfile profile = profile("01012345678", "서울시 종로구", "101동", "에누리은행", "123-456-789");
        when(fieldCryptoService.encrypt("01012345678")).thenReturn("enc-telno");
        when(fieldCryptoService.encrypt("서울시 종로구")).thenReturn("enc-address");
        when(fieldCryptoService.encrypt("101동")).thenReturn("enc-detail-address");
        when(fieldCryptoService.encrypt("에누리은행")).thenReturn("enc-bank-name");
        when(fieldCryptoService.encrypt("123-456-789")).thenReturn("enc-account-no");

        memberAuthAdapter.registerOAuthMember(profile);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberMapper).saveCertifiedMember(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getUsrTelno()).isEqualTo("enc-telno");
        assertThat(savedMember.getUsrAddr()).isEqualTo("enc-address");
        assertThat(savedMember.getUsrDaddr()).isEqualTo("enc-detail-address");
        assertThat(savedMember.getUsrBankNm()).isEqualTo("enc-bank-name");
        assertThat(savedMember.getUsrAcntNo()).isEqualTo("enc-account-no");
        verify(fieldCryptoService).encrypt("01012345678");
        verify(fieldCryptoService).encrypt("서울시 종로구");
        verify(fieldCryptoService).encrypt("101동");
        verify(fieldCryptoService).encrypt("에누리은행");
        verify(fieldCryptoService).encrypt("123-456-789");
    }

    @Test
    void OAuth_선택정보를_입력하지_않으면_null을_그대로_저장한다() {
        OAuthProfile profile = profile(null, null, null, null, null);
        when(fieldCryptoService.encrypt(isNull())).thenReturn(null);

        memberAuthAdapter.registerOAuthMember(profile);

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberMapper).saveCertifiedMember(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getUsrTelno()).isNull();
        assertThat(savedMember.getUsrAddr()).isNull();
        assertThat(savedMember.getUsrDaddr()).isNull();
        assertThat(savedMember.getUsrBankNm()).isNull();
        assertThat(savedMember.getUsrAcntNo()).isNull();
        verify(fieldCryptoService, times(5)).encrypt(isNull());
    }

    private OAuthProfile profile(String telno, String address, String detailAddress, String bankName, String accountNo) {
        return OAuthProfile.builder()
                .provider("USRC0004").providerKey("provider-user-1")
                .email("oauth@example.com").nickname("온보딩회원")
                .telno(telno).address(address).detailAddress(detailAddress)
                .bankName(bankName).accountNo(accountNo)
                .build();
    }
}
