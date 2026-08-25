package nct.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.quote.domain.Quote;
import nct.quote.dto.QuoteSanctionTarget;
import nct.quote.mapper.QuoteMapper;
import nct.quote.port.QuoteEnforcementImpact;

/** 담당자 7 · F-OPS-008: 기간 제재 견적 보류와 영구 제재 철회 경계를 검증합니다. */
class QuoteSanctionEnforcementServiceTest {

    private QuoteMapper quoteMapper;
    private QuoteSanctionEnforcementService service;

    @BeforeEach
    void setUp() {
        quoteMapper = mock(QuoteMapper.class);
        service = new QuoteSanctionEnforcementService(quoteMapper);
    }

    @Test
    void temporarySuspensionRecordsOnlyProvidersActiveQuotes() {
        QuoteSanctionTarget providerActive = target(101L, 11L, 20L, "QUTC0001");
        QuoteSanctionTarget requesterOwned = target(102L, 20L, 11L, "QUTC0001");
        QuoteSanctionTarget selected = target(103L, 11L, 20L, "QUTC0004");
        when(quoteMapper.findSanctionTargetsByMemberForUpdate(11L))
                .thenReturn(List.of(providerActive, requesterOwned, selected));

        List<QuoteEnforcementImpact> impacts =
                service.pauseActiveQuotes(11L, 99L, "7일 이용정지");

        assertThat(impacts).singleElement().satisfies(impact -> {
            assertThat(impact.quoteId()).isEqualTo(101L);
            assertThat(impact.roleCode()).isEqualTo("PROVIDER");
            assertThat(impact.actionCode()).isEqualTo("PAUSED");
            assertThat(impact.previousStatusCode()).isEqualTo("QUTC0001");
        });
    }

    @Test
    void temporaryReleaseSucceedsOnlyWhenQuoteStateIsUnchanged() {
        Quote quote = Quote.builder().qutSn(101L).qutStatusCd("QUTC0001").build();
        when(quoteMapper.findQuoteByIdForUpdate(101L)).thenReturn(quote);

        assertThat(service.restorePausedQuote(101L, "QUTC0001", 99L)).isTrue();
        assertThat(service.restorePausedQuote(101L, "QUTC0002", 99L)).isFalse();
    }

    @Test
    void permanentSuspensionStillRejectsUnexpectedQuoteStateChange() {
        QuoteSanctionTarget active = target(101L, 11L, 20L, "QUTC0001");
        when(quoteMapper.findSanctionTargetsByMemberForUpdate(11L)).thenReturn(List.of(active));
        when(quoteMapper.withdrawQuoteForSanction(101L, "QUTC0001", "99"))
                .thenReturn(0);

        assertThatThrownBy(() -> service.withdrawActiveQuotes(11L, 99L, "영구정지"))
                .isInstanceOf(CustomException.class);
    }

    private QuoteSanctionTarget target(
            Long quoteId,
            Long providerUserSn,
            Long requestOwnerUserSn,
            String statusCode) {
        QuoteSanctionTarget target = new QuoteSanctionTarget();
        target.setQuoteId(quoteId);
        target.setProviderUserSn(providerUserSn);
        target.setRequestOwnerUserSn(requestOwnerUserSn);
        target.setStatusCode(statusCode);
        return target;
    }
}
