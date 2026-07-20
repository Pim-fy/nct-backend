package nct.global.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;

// @ai_generated
/** F-AUTH-009: 매 인증 요청마다 정지/탈퇴 계정을 차단하는지 검증한다(usrSn 기준 조회). */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private AuthMemberPort authMemberPort;

    private CustomUserDetailsService customUserDetailsService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        customUserDetailsService = new CustomUserDetailsService(authMemberPort);
    }

    @Test
    void 활성_상태_회원은_정상적으로_UserDetails를_반환한다() {
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(member("USRC0001")));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("101");

        assertThat(userDetails).isNotNull();
    }

    @Test
    void 정지_상태_회원은_ACCOUNT_SUSPENDED로_차단한다() {
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(member("USRC0002")));

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("101"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    void 탈퇴_상태_회원은_WITHDRAWN_USER로_차단한다() {
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(member("USRC0003")));

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("101"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWN_USER);
    }

    @Test
    void 존재하지_않는_회원은_USER_NOT_FOUND를_던진다() {
        when(authMemberPort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("999"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private AuthMember member(String status) {
        return AuthMember.builder()
                .id(101L).email("user@example.com").role("ROLE_USER").status(status).build();
    }
}
