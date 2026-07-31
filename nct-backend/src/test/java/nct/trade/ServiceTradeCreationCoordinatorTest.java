package nct.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.trade.dto.ServiceTradeCreateCommand;
import nct.trade.dto.ServiceTradeCreateResult;
import nct.trade.port.SelectedServiceQuote;
import nct.trade.port.SelectedServiceQuoteReader;
import nct.trade.port.ServiceEscrowCreateCommand;
import nct.trade.port.ServiceEscrowCreator;
import nct.trade.port.ServiceTradeCreator;
import nct.trade.service.ServiceTradeCreationCoordinator;

class ServiceTradeCreationCoordinatorTest {

    @Test
    void createsTradeWithServerSelectedQuoteThenCreatesEscrow() {
        FakeSelectedQuoteReader quoteReader = new FakeSelectedQuoteReader(
                new SelectedServiceQuote(31L, 41L, 11L, 22L, BigDecimal.valueOf(150000)));
        FakeServiceTradeCreator tradeCreator = new FakeServiceTradeCreator(
                new ServiceTradeCreateResult(91L, "TRDC0003", true));
        FakeServiceEscrowCreator escrowCreator = new FakeServiceEscrowCreator();
        ServiceTradeCreationCoordinator coordinator = new ServiceTradeCreationCoordinator(
                quoteReader, tradeCreator, escrowCreator);

        ServiceTradeCreateResult result = coordinator.create(11L, 31L, 41L);

        assertThat(result.getTradeId()).isEqualTo(91L);
        assertThat(tradeCreator.command).isEqualTo(new ServiceTradeCreateCommand(
                11L, 22L, 31L, 41L, BigDecimal.valueOf(150000)));
        assertThat(escrowCreator.command).isEqualTo(
                new ServiceEscrowCreateCommand(91L, 11L, 150000L));
    }

    @Test
    void rejectsQuoteReaderResultThatDoesNotMatchRequestedQuote() {
        ServiceTradeCreationCoordinator coordinator = new ServiceTradeCreationCoordinator(
                (requesterUserId, serviceRequestId, quoteId) ->
                        new SelectedServiceQuote(31L, 42L, 11L, 22L, BigDecimal.valueOf(150000)),
                command -> new ServiceTradeCreateResult(91L, "TRDC0003", true),
                command -> { });

        assertThatThrownBy(() -> coordinator.create(11L, 31L, 41L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void doesNotCreateEscrowAgainWhenSelectedQuoteAlreadyHasTrade() {
        FakeServiceEscrowCreator escrowCreator = new FakeServiceEscrowCreator();
        ServiceTradeCreationCoordinator coordinator = new ServiceTradeCreationCoordinator(
                new FakeSelectedQuoteReader(selectedQuote()),
                new FakeServiceTradeCreator(new ServiceTradeCreateResult(91L, "TRDC0003", false)),
                escrowCreator);

        ServiceTradeCreateResult result = coordinator.create(11L, 31L, 41L);

        assertThat(result.isCreated()).isFalse();
        assertThat(escrowCreator.command).isNull();
    }

    @Test
    void propagatesEscrowFailureToOuterTransaction() {
        ServiceTradeCreationCoordinator coordinator = new ServiceTradeCreationCoordinator(
                new FakeSelectedQuoteReader(selectedQuote()),
                new FakeServiceTradeCreator(new ServiceTradeCreateResult(91L, "TRDC0003", true)),
                command -> {
                    throw new CustomException(nct.global.exception.ErrorCode.CONFLICT,
                            "보관금 생성에 실패했습니다.");
                });

        assertThatThrownBy(() -> coordinator.create(11L, 31L, 41L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("보관금 생성에 실패했습니다.");
    }

    private SelectedServiceQuote selectedQuote() {
        return new SelectedServiceQuote(31L, 41L, 11L, 22L, BigDecimal.valueOf(150000));
    }

    private static final class FakeSelectedQuoteReader implements SelectedServiceQuoteReader {
        private final SelectedServiceQuote quote;

        private FakeSelectedQuoteReader(SelectedServiceQuote quote) {
            this.quote = quote;
        }

        @Override
        public SelectedServiceQuote lockSelectedQuoteForTradeCreation(
                long requesterUserId, long serviceRequestId, long quoteId) {
            return quote;
        }
    }

    private static final class FakeServiceTradeCreator implements ServiceTradeCreator {
        private final ServiceTradeCreateResult result;
        private ServiceTradeCreateCommand command;

        private FakeServiceTradeCreator(ServiceTradeCreateResult result) {
            this.result = result;
        }

        @Override
        public ServiceTradeCreateResult createOrGetServiceTrade(ServiceTradeCreateCommand command) {
            this.command = command;
            return result;
        }
    }

    private static final class FakeServiceEscrowCreator implements ServiceEscrowCreator {
        private ServiceEscrowCreateCommand command;

        @Override
        public void createEscrow(ServiceEscrowCreateCommand command) {
            this.command = command;
        }
    }
}
