package nct.global.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;
import nct.ops.sanction.port.SanctionStatusReader;
import nct.provider.service.ProviderApplicationService;

class ProviderAccessGuardTest {

    private final ProviderApplicationService providerApplicationService = mock(ProviderApplicationService.class);
    private final SanctionStatusReader sanctionStatusReader = mock(SanctionStatusReader.class);
    private final ProviderAccessGuard guard = new ProviderAccessGuard(providerApplicationService, sanctionStatusReader);

    @Test
    void 제공자_역할과_승인카테고리와_제재없음이_모두_충족되면_통과한다() {
        doNothing().when(providerApplicationService).requireCategoryPermission(101L, 30L);
        doNothing().when(sanctionStatusReader).requireNoActiveSanction(101L);

        assertThat(guard.requireServiceAccess(authentication(101L, "ROLE_SERVICE"), 30L)).isEqualTo(101L);

        verify(providerApplicationService).requireCategoryPermission(101L, 30L);
        verify(sanctionStatusReader).requireNoActiveSanction(101L);
    }

    @Test
    void 일반사용자는_타도메인_조회_전에_차단한다() {
        assertThatThrownBy(() -> guard.requireServiceAccess(authentication(101L, "ROLE_USER"), 30L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(providerApplicationService, never()).requireCategoryPermission(101L, 30L);
        verify(sanctionStatusReader, never()).requireNoActiveSanction(101L);
    }

    @Test
    void 카테고리번호가_유효하지_않으면_타도메인_조회_전에_차단한다() {
        assertThatThrownBy(() -> guard.requireServiceAccess(authentication(101L, "ROLE_SERVICE"), 0L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(providerApplicationService, never()).requireCategoryPermission(101L, 0L);
        verify(sanctionStatusReader, never()).requireNoActiveSanction(101L);
    }

    @Test
    void 유효제재가_있으면_카테고리승인_확인_뒤_차단한다() {
        doNothing().when(providerApplicationService).requireCategoryPermission(101L, 30L);
        doThrow(new CustomException(ErrorCode.FORBIDDEN)).when(sanctionStatusReader).requireNoActiveSanction(101L);

        assertThatThrownBy(() -> guard.requireServiceAccess(authentication(101L, "ROLE_SERVICE"), 30L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(providerApplicationService).requireCategoryPermission(101L, 30L);
        verify(sanctionStatusReader).requireNoActiveSanction(101L);
    }

    @Test
    void 승인카테고리가_아니면_제재상태_조회_전에_차단한다() {
        doThrow(new CustomException(ErrorCode.FORBIDDEN))
                .when(providerApplicationService).requireCategoryPermission(101L, 30L);

        assertThatThrownBy(() -> guard.requireServiceAccess(authentication(101L, "ROLE_SERVICE"), 30L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(sanctionStatusReader, never()).requireNoActiveSanction(101L);
    }

    @Test
    void JWT_주체가_아니면_타도메인_조회_전에_차단한다() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("user", null,
                List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));

        assertThatThrownBy(() -> guard.requireServiceAccess(authentication, 30L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(providerApplicationService, never()).requireCategoryPermission(101L, 30L);
        verify(sanctionStatusReader, never()).requireNoActiveSanction(101L);
    }

    private UsernamePasswordAuthenticationToken authentication(Long userSn, String role) {
        CustomUserDetails userDetails = new CustomUserDetails(AuthMember.builder()
                .id(userSn)
                .email("service@example.com")
                .role(role)
                .build());
        return new UsernamePasswordAuthenticationToken(userDetails, null,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
