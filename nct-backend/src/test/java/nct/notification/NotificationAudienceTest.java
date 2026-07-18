package nct.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import nct.notification.domain.NotificationAudience;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [테스트 - 알림 일반/제공자 구분 저장] (F-COM-011, 팀 결정: ⓐ분류표+ⓑ구분 컬럼)
 *
 * 공유 DB(NCTDB) 주의사항 — 다른 테스트와 동일:
 * @Transactional 테스트라 종료 시 전부 롤백, 행을 남기지 않는다.
 */
@SpringBootTest
@Transactional
class NotificationAudienceTest {

    @Autowired NotificationService notificationService;
    @Autowired JdbcTemplate jdbc;

    long usrSn;

    @BeforeEach
    void setUpUser() {
        String loginId = "t_aud_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, loginId, loginId + "@test.local");
        usrSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    @DisplayName("기존 시그니처로 발행하면 자동으로 '일반'으로 저장된다 (타 담당자 호출 코드 무수정 호환)")
    void defaultAudienceIsGeneral() {
        notificationService.notifyCharge(usrSn, 10_000); // 구분 미지정 발행 경로

        assertThat(notificationService.getList(usrSn))
                .singleElement()
                .satisfies(n -> assertThat(n.getNtfAudienceCd())
                        .isEqualTo(NotificationAudience.GENERAL.getCode()));
    }

    @Test
    @DisplayName("정산 알림은 '제공자'로 저장된다 (분류표: 판매대금은 제공자 업무)")
    void settlementIsProvider() {
        notificationService.notifySettlement(usrSn, "정산 완료", "판매대금이 정산되었습니다.", 1L);

        assertThat(notificationService.getList(usrSn))
                .singleElement()
                .satisfies(n -> assertThat(n.getNtfAudienceCd())
                        .isEqualTo(NotificationAudience.PROVIDER.getCode()));
    }

    @Test
    @DisplayName("PROVIDER를 지정한 범용 발행도 '제공자'로 저장된다")
    void explicitProviderAudience() {
        notificationService.notify(usrSn, NotificationType.SERVICE, NotificationDomain.SERVICE,
                NotificationAudience.PROVIDER, "견적 요청 도착", "새 견적 요청이 있습니다.", null, null);

        assertThat(notificationService.getList(usrSn))
                .singleElement()
                .satisfies(n -> assertThat(n.getNtfAudienceCd())
                        .isEqualTo(NotificationAudience.PROVIDER.getCode()));
    }
}
