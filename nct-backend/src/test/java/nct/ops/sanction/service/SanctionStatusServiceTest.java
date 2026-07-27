package nct.ops.sanction.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.sanction.mapper.SanctionMapper;

class SanctionStatusServiceTest {

    private final SanctionMapper sanctionMapper = mock(SanctionMapper.class);
    private final SanctionStatusService service = new SanctionStatusService(sanctionMapper);

    @Test
    void 유효한_제재가_없으면_통과한다() {
        when(sanctionMapper.existsActiveSanction(101L)).thenReturn(false);

        assertThatCode(() -> service.requireNoActiveSanction(101L)).doesNotThrowAnyException();
    }

    @Test
    void 유효한_제재가_있으면_제공자_접근을_차단한다() {
        when(sanctionMapper.existsActiveSanction(101L)).thenReturn(true);

        assertThatThrownBy(() -> service.requireNoActiveSanction(101L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 회원번호가_없으면_제재_조회_전에_인증오류로_차단한다() {
        assertThatThrownBy(() -> service.requireNoActiveSanction(null))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
