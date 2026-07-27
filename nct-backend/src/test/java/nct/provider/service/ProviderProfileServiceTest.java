package nct.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.sanction.port.SanctionStatusReader;
import nct.provider.dto.ProviderProfileRequest;
import nct.provider.dto.ProviderProfileResponse;
import nct.provider.mapper.ProviderProfileMapper;

/** 담당자 7, F-PROV-004: 프로필 변경 전 제공자 활성·제재 검증 회귀 테스트다. */
@ExtendWith(MockitoExtension.class)
class ProviderProfileServiceTest {
    @Mock private ProviderProfileMapper mapper;
    @Mock private ProviderApplicationService providerApplicationService;
    @Mock private SanctionStatusReader sanctionStatusReader;
    @InjectMocks private ProviderProfileService service;

    @Test
    void updateMineChecksActiveProviderAndReturnsSavedProfile() {
        ProviderProfileRequest request = new ProviderProfileRequest();
        request.setIntroduction("소개");
        request.setAvailableArea("서울");
        ProviderProfileResponse saved = profile(101L);
        when(mapper.findActiveByUserSn(101L)).thenReturn(Optional.of(saved));

        ProviderProfileResponse result = service.updateMine(101L, request);

        assertThat(result.getUserSn()).isEqualTo(101L);
        verify(providerApplicationService).requireAnyActivePermission(101L);
        verify(sanctionStatusReader).requireNoActiveSanction(101L);
        verify(mapper).upsert(101L, "소개", "서울", "101");
    }

    @Test
    void suspendedProviderCannotReadPublicProfile() {
        doThrow(new CustomException(ErrorCode.FORBIDDEN)).when(sanctionStatusReader).requireNoActiveSanction(101L);

        assertThatThrownBy(() -> service.getPublic(101L))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private ProviderProfileResponse profile(Long userSn) {
        return ProviderProfileResponse.builder().userSn(userSn).introduction("소개").availableArea("서울")
                .reviewAverageScore(BigDecimal.ZERO).reviewCount(0L).build();
    }
}
