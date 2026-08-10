package nct.ops.reference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.domain.CommonCode;

/** 담당자 7 · 새 경매가 활성 AUCG02 금액만 받는지 검증합니다. */
@ExtendWith(MockitoExtension.class)
class AuctionBidUnitPolicyServiceTest {

    @Mock
    private ReferenceDataService referenceDataService;

    @InjectMocks
    private AuctionBidUnitPolicyService service;

    @Test
    void returnsRequestedActiveAmount() {
        when(referenceDataService.getActiveCodes("AUCG02"))
                .thenReturn(List.of(code("500"), code("1000")));

        assertThat(service.resolveActiveAmount(BigDecimal.valueOf(500)))
                .isEqualByComparingTo("500");
    }

    @Test
    void usesSmallestActiveAmountWhenLegacyCallerOmitsValue() {
        when(referenceDataService.getActiveCodes("AUCG02"))
                .thenReturn(List.of(code("5000"), code("1000")));

        assertThat(service.resolveActiveAmount(null)).isEqualByComparingTo("1000");
    }

    @Test
    void rejectsInactiveOrUnknownAmount() {
        when(referenceDataService.getActiveCodes("AUCG02"))
                .thenReturn(List.of(code("1000")));

        assertThatThrownBy(() -> service.resolveActiveAmount(BigDecimal.valueOf(500)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    private CommonCode code(String name) {
        CommonCode code = new CommonCode();
        code.setName(name);
        code.setUseYn("Y");
        return code;
    }
}
