package nct.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;

import nct.abuse.port.ActiveAbuseReportReferenceReader;
import nct.auction.constant.AuctionStatusCode;
import nct.auction.dto.AuctionBidTarget;
import nct.auction.dto.AuctionSanctionTarget;
import nct.auction.mapper.AuctionCancelRequestMapper;
import nct.auction.mapper.AuctionMapper;
import nct.auction.port.AuctionEnforcementImpact;
import nct.auction.port.MemberAuctionEnforcementCommand;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.service.NotificationService;
import nct.ops.reference.service.ReferenceDataService;
import nct.point.service.PointService;
import nct.trade.port.ActiveTradeIncidentReader;
import nct.trade.service.TradeService;

/** 담당자 7 · F-OPS-008: 내부 검토 보류 예외가 상위 제재 트랜잭션을 롤백시키지 않는지 검증합니다. */
class AuctionSanctionTransactionBoundaryTest {

    @Test
    void reviewRequiredExceptionDoesNotMarkSharedTransactionRollbackOnly() {
        BoundaryFixture fixture = fixture();

        AuctionBidTarget changedAuction = mock(AuctionBidTarget.class);
        when(changedAuction.getAuctionStatusCode()).thenReturn("AUCC9999");
        when(fixture.auctionMapper().findAuctionBidTargetForUpdate(101L))
                .thenReturn(changedAuction);

        List<AuctionEnforcementImpact> impacts =
                fixture.enforcementService().cancelForPermanentSuspension(command());

        assertThat(impacts).singleElement().satisfies(impact ->
                assertThat(impact.actionCode()).isEqualTo("HELD_FOR_REVIEW"));
        assertThat(fixture.transactionManager().commitCount).isEqualTo(1);
        assertThat(fixture.transactionManager().rollbackCount).isZero();
    }

    @Test
    void unexpectedDatabaseFailureStillRollsBackSharedTransaction() {
        BoundaryFixture fixture = fixture();
        when(fixture.auctionMapper().findAuctionBidTargetForUpdate(101L))
                .thenThrow(new CustomException(ErrorCode.DATABASE_ERROR));

        assertThatThrownBy(() ->
                fixture.enforcementService().cancelForPermanentSuspension(command()))
                .isInstanceOf(CustomException.class);
        assertThat(fixture.transactionManager().commitCount).isZero();
        assertThat(fixture.transactionManager().rollbackCount).isEqualTo(1);
    }

    private BoundaryFixture fixture() {
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        AuctionMapper auctionMapper = mock(AuctionMapper.class);
        ActiveAbuseReportReferenceReader reportReader =
                mock(ActiveAbuseReportReferenceReader.class);

        AuctionCancellationService cancellationTarget = new AuctionCancellationService(
                auctionMapper,
                mock(AuctionCancelRequestMapper.class),
                mock(ReferenceDataService.class),
                mock(TradeService.class),
                mock(PointService.class),
                reportReader,
                mock(NotificationService.class));
        AuctionCancellationService cancellationProxy =
                transactionalProxy(cancellationTarget, transactionManager);

        AuctionSanctionEnforcementService enforcementTarget =
                new AuctionSanctionEnforcementService(
                        auctionMapper,
                        mock(ReferenceDataService.class),
                        cancellationProxy,
                        mock(PointService.class),
                        reportReader,
                        mock(ActiveTradeIncidentReader.class));
        AuctionSanctionEnforcementService enforcementProxy =
                transactionalProxy(enforcementTarget, transactionManager);

        AuctionSanctionTarget sanctionTarget = new AuctionSanctionTarget();
        sanctionTarget.setAuctionId(101L);
        sanctionTarget.setSellerUserSn(11L);
        sanctionTarget.setHighestBidderUserSn(20L);
        sanctionTarget.setAuctionStatusCode(AuctionStatusCode.ACTIVE);
        when(auctionMapper.findSanctionTargetsByMemberForUpdate(11L))
                .thenReturn(List.of(sanctionTarget));

        return new BoundaryFixture(transactionManager, auctionMapper, enforcementProxy);
    }

    private MemberAuctionEnforcementCommand command() {
        return new MemberAuctionEnforcementCommand(
                11L,
                99L,
                "영구 이용정지",
                "report-sanction-transaction-boundary-test",
                null,
                501L);
    }

    @SuppressWarnings("unchecked")
    private <T> T transactionalProxy(T target, TrackingTransactionManager transactionManager) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        proxyFactory.addAdvice(interceptor);
        return (T) proxyFactory.getProxy();
    }

    private static final class TrackingTransactionManager
            extends AbstractPlatformTransactionManager {

        private static final long serialVersionUID = 1L;

        private final ThreadLocal<TrackingTransaction> current = new ThreadLocal<>();
        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            TrackingTransaction transaction = current.get();
            return transaction == null ? new TrackingTransaction() : transaction;
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TrackingTransaction) transaction).active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            TrackingTransaction tracking = (TrackingTransaction) transaction;
            tracking.active = true;
            current.set(tracking);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {
            ((TrackingTransaction) status.getTransaction()).rollbackOnly = true;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            ((TrackingTransaction) transaction).active = false;
            current.remove();
        }
    }

    private static final class TrackingTransaction implements SmartTransactionObject {

        private boolean active;
        private boolean rollbackOnly;

        @Override
        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        @Override
        public void flush() {
            // No resources are written; this manager only verifies proxy rollback semantics.
        }
    }

    private record BoundaryFixture(
            TrackingTransactionManager transactionManager,
            AuctionMapper auctionMapper,
            AuctionSanctionEnforcementService enforcementService) {}
}
