package nct.support;

import org.junit.jupiter.api.extension.ExtendWith;

/** 공유 DB 쓰기 승인이 명시된 경우에만 통합 테스트를 실행하는 기반 클래스입니다. */
@ExtendWith(ApprovedDatabaseWriteCondition.class)
public abstract class ApprovedDatabaseWriteIntegrationTest extends SafeSpringBootIntegrationTest {

    static String setting(String propertyName, String environmentName) {
        String property = System.getProperty(propertyName);
        return property == null || property.isBlank() ? System.getenv(environmentName) : property;
    }
}
