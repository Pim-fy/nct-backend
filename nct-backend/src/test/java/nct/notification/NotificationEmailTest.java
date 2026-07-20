package nct.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import nct.notification.service.NotificationMailSender;
import nct.notification.service.NotificationService;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [테스트 - 중요 이벤트 이메일 보조 발송] (F-COM-006, 트리거·조건 확정 2026-07-18)
 *
 * 발송기(NotificationMailSender)는 가짜로 바꿔서 실제 메일이 나가지 않게 한다 —
 * 검증 대상은 "언제 보내기로 판정하는지"와 "결과가 알림 행에 어떻게 기록되는지"다.
 *
 * 공유 DB(NCTDB) 주의사항 (PointFlowTest와 동일):
 * - @Transactional 테스트는 메소드 종료 시 전부 롤백되어 행을 남기지 않는다
 *   (SYSTEM_SETTING 스위치 변경도 원상복구된다)
 */
@SpringBootTest
@Transactional
class NotificationEmailTest {

    @Autowired NotificationService notificationService;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean NotificationMailSender mailSender;

    long usrSn;
    String userEmail;

    @BeforeEach
    void setUpUser() {
        String loginId = "t_mail_" + System.nanoTime();
        userEmail = loginId + "@test.local";
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', 't_mail', ?, 'USRC0001', 'ROLE_USER')
                """, loginId, userEmail);
        usrSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        // 기본: 메일 설정이 있는 환경 + 발송 성공 가정 (케이스별로 덮어씀)
        when(mailSender.isAvailable()).thenReturn(true);
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("발송: 조건 충족 시 회원 이메일로 발송되고 알림 행에 '성공'이 기록된다")
    void sendsWhenEligible() {
        notificationService.notifyTradeConfirmRequest(usrSn, 1L, 5);

        verify(mailSender).send(eq(userEmail), contains("거래 완료 확인 요청"), contains("자동으로 완료"));
        assertThat(emailStatusOfLatest()).isEqualTo("NTFC0008"); // 성공
        assertThat(notificationService.getUnreadCount(usrSn)).isEqualTo(1); // 인앱 알림도 생성
    }

    @Test
    @DisplayName("차단: 회원이 거래 도메인 이메일 수신을 꺼두면 발송하지 않고 '미대상' 기록")
    void skipsWhenUserToggleOff() {
        jdbc.update("""
                INSERT INTO USER_NOTIFICATION_SETTING
                    (USR_SN, USR_NTF_STG_AUC_INAPP_YN, USR_NTF_STG_AUC_EMAIL_YN,
                     USR_NTF_STG_TRD_INAPP_YN, USR_NTF_STG_TRD_EMAIL_YN,
                     USR_NTF_STG_SVC_INAPP_YN, USR_NTF_STG_SVC_EMAIL_YN)
                VALUES (?, 'Y', 'Y', 'Y', 'N', 'Y', 'Y')
                """, usrSn);

        notificationService.notifyDisputeReceived(usrSn, 1L);

        verify(mailSender, never()).send(anyString(), anyString(), anyString());
        assertThat(emailStatusOfLatest()).isEqualTo("NTFC0006"); // 미대상
        assertThat(notificationService.getUnreadCount(usrSn)).isEqualTo(1); // 인앱은 그대로
    }

    @Test
    @DisplayName("차단: 관리자 전역 스위치(SYS_SET_EMAIL_YN)가 N이면 토글과 무관하게 발송하지 않는다")
    void skipsWhenGlobalSwitchOff() {
        jdbc.update("UPDATE SYSTEM_SETTING SET SYS_SET_EMAIL_YN = 'N'"); // 롤백으로 원상복구됨

        notificationService.notifyExchangeComplete(usrSn, 30000);

        verify(mailSender, never()).send(anyString(), anyString(), anyString());
        assertThat(emailStatusOfLatest()).isEqualTo("NTFC0006");
    }

    @Test
    @DisplayName("베스트 에포트: 발송이 실패해도 인앱 알림은 생성되고 상태만 '실패'로 남는다")
    void failureDoesNotBreakNotification() {
        when(mailSender.send(anyString(), anyString(), anyString())).thenReturn(false);

        notificationService.notifyExchangeReject(usrSn, 30000, "계좌 오류");

        assertThat(emailStatusOfLatest()).isEqualTo("NTFC0009"); // 실패
        assertThat(notificationService.getUnreadCount(usrSn)).isEqualTo(1); // 인앱 알림은 정상
    }

    @Test
    @DisplayName("차단: 메일 미설정 환경(팀원 PC)에서는 발송 시도 없이 '미대상' — 서비스는 정상 동작")
    void skipsWhenMailUnavailable() {
        when(mailSender.isAvailable()).thenReturn(false);

        notificationService.notifyTradeConfirmRequest(usrSn, 1L, 5);

        verify(mailSender, never()).send(anyString(), anyString(), anyString());
        assertThat(emailStatusOfLatest()).isEqualTo("NTFC0006");
        assertThat(notificationService.getUnreadCount(usrSn)).isEqualTo(1);
    }

    // ---------- 픽스처 ----------

    /** 방금 만든 회원의 최신 알림 행 이메일 발송 상태 */
    private String emailStatusOfLatest() {
        return jdbc.queryForObject("""
                SELECT NTF_EMAIL_STATUS_CD FROM NOTIFICATION
                WHERE USR_SN = ? ORDER BY NTF_SN DESC LIMIT 1
                """, String.class, usrSn);
    }
}
