package nct.provider.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.provider.mapper.ProviderApplicationMapper;

// @ai_generated F-PROV-015: 모드 진입용 "활성 권한 하나 이상" 읽기 계약을 단위 검증한다.
class ProviderApplicationServicePermissionTest {

    private final ProviderApplicationMapper mapper = mock(ProviderApplicationMapper.class);
    private final ProviderApplicationService service = new ProviderApplicationService(
            mapper,
            mock(ReferenceDataService.class),
            mock(FileStorageService.class));

    @Test
    void 활성_제공자_권한이_하나라도_있으면_통과한다() {
        when(mapper.hasAnyActivePermission(101L)).thenReturn(true);

        assertThatCode(() -> service.requireAnyActivePermission(101L)).doesNotThrowAnyException();
    }

    @Test
    void 활성_제공자_권한이_없으면_차단한다() {
        when(mapper.hasAnyActivePermission(101L)).thenReturn(false);

        assertThatThrownBy(() -> service.requireAnyActivePermission(101L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
