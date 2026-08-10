package nct.support;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** DB 접속 없이 @Transactional 경계의 commit/rollback 선택을 검증합니다. */
public final class TransactionTestSupport {

    private TransactionTestSupport() {
    }

    public static <T> T transactionalProxy(
            T target, Class<T> targetType, RecordingTransactionManager transactionManager) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                (TransactionManager) transactionManager,
                new AnnotationTransactionAttributeSource()));
        return targetType.cast(proxyFactory.getProxy());
    }

    public static final class RecordingTransactionManager implements PlatformTransactionManager {

        private int commitCount;
        private int rollbackCount;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commitCount++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbackCount++;
        }

        public int commitCount() {
            return commitCount;
        }

        public int rollbackCount() {
            return rollbackCount;
        }
    }
}
