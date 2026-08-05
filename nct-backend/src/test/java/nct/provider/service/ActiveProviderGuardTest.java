package nct.provider.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.port.AuthMember;
import nct.global.security.port.AuthMemberPort;
import nct.ops.sanction.port.SanctionStatusReader;

/**
 * Claude Code 작성 (BJN, 2026-08-05)
 *
 * 담당자 6 · 활성 제공자 검증 공통 가드(ActiveProviderGuard) 회귀 테스트.
 * 포트폴리오/프로필 서비스 테스트가 각자 검증하던 "회원 상태·승인 권한·제재" 판정 로직이
 * 가드로 통합되면서(2026-08-05 중복 정리), 그 판정 자체의 테스트도 여기로 옮겨 한 곳에서 지킨다.
 */
@ExtendWith(MockitoExtension.class)
class ActiveProviderGuardTest {
    @Mock private AuthMemberPort authMemberPort;
    @Mock private ProviderApplicationService providerApplicationService;
    @Mock private SanctionStatusReader sanctionStatusReader;
    @InjectMocks private ActiveProviderGuard guard;

    @Test
    void activeProviderPassesAllChecks() {
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(member(101L, "USRC0001")));

        guard.requireActive(101L);

        verify(providerApplicationService).requireAnyActivePermission(101L);
        verify(sanctionStatusReader).requireNoActiveSanction(101L);
    }

    @Test
    void invalidUserSnIsUnauthorized() {
        assertThatThrownBy(() -> guard.requireActive(null))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThatThrownBy(() -> guard.requireActive(0L))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(authMemberPort, providerApplicationService, sanctionStatusReader);
    }

    @Test
    void missingMemberIsNotFound() {
        when(authMemberPort.findById(101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireActive(101L))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void withdrawnMemberIsNotFound() {
        // 탈퇴 등 정상(USRC0001)이 아닌 상태는 존재 여부를 숨기기 위해 NOT_FOUND로 취급한다
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(member(101L, "USRC0003")));

        assertThatThrownBy(() -> guard.requireActive(101L))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(providerApplicationService, never()).requireAnyActivePermission(101L);
    }

    @Test
    void sanctionedProviderIsRejected() {
        when(authMemberPort.findById(101L)).thenReturn(Optional.of(member(101L, "USRC0001")));
        doThrow(new CustomException(ErrorCode.FORBIDDEN)).when(sanctionStatusReader).requireNoActiveSanction(101L);

        assertThatThrownBy(() -> guard.requireActive(101L))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private AuthMember member(Long userSn, String status) {
        return AuthMember.builder().id(userSn).status(status).build();
    }
}
