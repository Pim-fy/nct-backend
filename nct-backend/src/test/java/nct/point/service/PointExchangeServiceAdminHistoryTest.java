package nct.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import nct.audit.domain.AuditLogType;
import nct.audit.service.AuditLogService;
import nct.common.domain.RefType;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.global.security.crypto.FieldCryptoService;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.notification.service.NotificationService;
import nct.point.domain.PointExchangeOrder;
import nct.point.domain.PointExchangeOrderStatus;
import nct.point.dto.AdminPointExchangeAccountResponse;
import nct.point.dto.AdminPointExchangeOrderResponse;
import nct.point.exception.PointException;
import nct.point.mapper.PointExchangeOrderMapper;

/** 담당자 7 · F-PAY-012/F-OPS-015: 환전 처리 이력과 계좌 원문 제한 조회를 검증합니다. */
class PointExchangeServiceAdminHistoryTest {

    private PointExchangeOrderMapper exchangeMapper;
    private NotificationService notificationService;
    private AuditLogService auditLogService;
    private FieldCryptoService fieldCryptoService;
    private PointExchangeService service;

    @BeforeEach
    void setUp() {
        exchangeMapper = mock(PointExchangeOrderMapper.class);
        notificationService = mock(NotificationService.class);
        auditLogService = mock(AuditLogService.class);
        fieldCryptoService = mock(FieldCryptoService.class);
        service = new PointExchangeService(
                exchangeMapper,
                mock(PointService.class),
                notificationService,
                auditLogService,
                fieldCryptoService);
    }

    @Test
    void pagesAllAdminOrdersWithNormalizedFilters() {
        PointExchangeOrder order = order(
                21L,
                7L,
                PointExchangeOrderStatus.COMPLETED,
                "enc-bank",
                "enc-account");
        when(exchangeMapper.countAdminList("PEOC0002", "홍길동")).thenReturn(21L);
        when(exchangeMapper.selectAdminList("PEOC0002", "홍길동", 20L, 20))
                .thenReturn(List.of(order));
        when(fieldCryptoService.decrypt("enc-bank")).thenReturn("국민은행");
        when(fieldCryptoService.decrypt("enc-account")).thenReturn("123-45-6789");

        PageResponse<PointExchangeOrder> result = service.getAdminOrderPage(
                " PEOC0002 ",
                " 홍길동 ",
                2,
                20);

        assertThat(result.getContent()).containsExactly(order);
        assertThat(result.getTotalCount()).isEqualTo(21);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.isHasNext()).isFalse();
        assertThat(order.getPtExcOrdBankNm()).isEqualTo("국민은행");
        assertThat(order.getPtExcOrdAcntNo()).isEqualTo("123-45-6789");
        verify(exchangeMapper).countAdminList("PEOC0002", "홍길동");
        verify(exchangeMapper).selectAdminList("PEOC0002", "홍길동", 20L, 20);
    }

    @Test
    void rejectsInvalidAdminSearchConditionsBeforeQuerying() {
        assertThatThrownBy(() -> service.getAdminOrderPage("PEOC9999", null, 1, 20))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> service.getAdminOrderPage(null, "가".repeat(101), 1, 20))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> service.getAdminOrderPage(null, null, 0, 20))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(exchangeMapper);
    }

    @Test
    void rejectsInvalidRejectReasonBeforeLockingOrRestoringPoints() {
        assertThatThrownBy(() -> service.reject(10L, 7L, " "))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> service.reject(10L, 7L, "가".repeat(501)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(exchangeMapper);
    }

    @Test
    void listResponseMasksAccountAndIncludesProcessingHistory() {
        PointExchangeOrder order = order(
                33L,
                8L,
                PointExchangeOrderStatus.REJECTED,
                "국민은행",
                "123-45-6789");
        order.setPtExcOrdProcUsrSn(99L);
        order.setPtExcOrdProcDt(LocalDateTime.of(2026, 8, 5, 14, 30));
        order.setPtExcOrdRjctRsnCn("계좌 정보 불일치");
        order.setStatusNm("반려");

        AdminPointExchangeOrderResponse response = AdminPointExchangeOrderResponse.from(order);

        assertThat(response.getAccountNo()).isEqualTo("*******6789");
        assertThat(response.getAccountNo()).doesNotContain("123");
        assertThat(response.getStatusCode()).isEqualTo("PEOC0003");
        assertThat(response.getProcessedBy()).isEqualTo(99L);
        assertThat(response.getProcessedDate()).isEqualTo("2026-08-05 14:30");
        assertThat(response.getRejectReason()).isEqualTo("계좌 정보 불일치");
    }

    @Test
    void requestedOrderWithoutProcessorBuildsAdminIdentityResponse() {
        PointExchangeOrder order = order(
                34L,
                8L,
                PointExchangeOrderStatus.REQUESTED,
                "국민은행",
                "123-45-6789");
        AdminMemberIdentityResponse applicant = AdminMemberIdentityResponse.builder()
                .userSn(8L)
                .loginId("member08")
                .nickname("환전신청자")
                .build();

        AdminPointExchangeOrderResponse response = AdminPointExchangeOrderResponse.from(
                order,
                Map.of(8L, applicant));

        assertThat(response.getApplicantMember()).isSameAs(applicant);
        assertThat(response.getProcessedBy()).isNull();
        assertThat(response.getProcessorMember()).isNull();
    }

    @Test
    void revealsRequestedAccountOnlyAfterSensitiveViewAudit() {
        PointExchangeOrder order = order(
                44L,
                10L,
                PointExchangeOrderStatus.REQUESTED,
                "enc-bank",
                "enc-account");
        when(exchangeMapper.selectForUpdateBySn(44L)).thenReturn(order);
        when(fieldCryptoService.decrypt("enc-bank")).thenReturn("신한은행");
        when(fieldCryptoService.decrypt("enc-account")).thenReturn("987-65-4321");

        AdminPointExchangeAccountResponse response = service.getRequestedAccountForAdmin(
                44L,
                7L,
                "127.0.0.1");

        assertThat(response.orderSn()).isEqualTo(44L);
        assertThat(response.bankName()).isEqualTo("신한은행");
        assertThat(response.accountNo()).isEqualTo("987-65-4321");

        InOrder auditBeforeDecrypt = inOrder(auditLogService, fieldCryptoService);
        auditBeforeDecrypt.verify(auditLogService).record(
                7L,
                AuditLogType.SENSITIVE_VIEW,
                RefType.POINT_EXCHANGE_ORDER,
                44L,
                "환전 신청 44번 지급 계좌 조회",
                null,
                null,
                null,
                RefType.MEMBER,
                10L,
                "127.0.0.1");
        auditBeforeDecrypt.verify(fieldCryptoService).decrypt("enc-bank");
        auditBeforeDecrypt.verify(fieldCryptoService).decrypt("enc-account");
    }

    @Test
    void blocksProcessedAccountRevealWithoutAuditOrDecryption() {
        PointExchangeOrder order = order(
                45L,
                10L,
                PointExchangeOrderStatus.COMPLETED,
                "enc-bank",
                "enc-account");
        when(exchangeMapper.selectForUpdateBySn(45L)).thenReturn(order);

        assertThatThrownBy(() -> service.getRequestedAccountForAdmin(45L, 7L, "127.0.0.1"))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXCHANGE_ORDER_ALREADY_PROCESSED);

        verify(auditLogService, never()).record(
                7L,
                AuditLogType.SENSITIVE_VIEW,
                RefType.POINT_EXCHANGE_ORDER,
                45L,
                "환전 신청 45번 지급 계좌 조회",
                null,
                null,
                null,
                RefType.MEMBER,
                10L,
                "127.0.0.1");
        verifyNoInteractions(fieldCryptoService);
    }

    @Test
    void blocksMissingAccountSnapshotForRevealAndCompletion() {
        PointExchangeOrder order = order(
                46L,
                10L,
                PointExchangeOrderStatus.REQUESTED,
                null,
                null);
        when(exchangeMapper.selectForUpdateBySn(46L)).thenReturn(order);

        assertThatThrownBy(() -> service.getRequestedAccountForAdmin(46L, 7L, "127.0.0.1"))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXCHANGE_ACCOUNT_NOT_REGISTERED);
        assertThatThrownBy(() -> service.complete(46L, 7L))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXCHANGE_ACCOUNT_NOT_REGISTERED);

        verify(exchangeMapper, never()).complete(
                46L,
                PointExchangeOrderStatus.COMPLETED.getCode(),
                7L);
        verifyNoInteractions(auditLogService, notificationService, fieldCryptoService);
    }

    @Test
    void completesRequestedOrderWhenAccountSnapshotIsValid() {
        PointExchangeOrder order = order(
                47L,
                10L,
                PointExchangeOrderStatus.REQUESTED,
                "enc-bank",
                "enc-account");
        when(exchangeMapper.selectForUpdateBySn(47L)).thenReturn(order);
        when(fieldCryptoService.decrypt("enc-bank")).thenReturn("신한은행");
        when(fieldCryptoService.decrypt("enc-account")).thenReturn("987-65-4321");

        service.complete(47L, 7L);

        verify(exchangeMapper).complete(
                47L,
                PointExchangeOrderStatus.COMPLETED.getCode(),
                7L);
        verify(notificationService).notifyExchangeComplete(10L, 30_000);
    }

    private PointExchangeOrder order(
            long orderSn,
            long userSn,
            PointExchangeOrderStatus status,
            String bankName,
            String accountNo) {
        PointExchangeOrder order = new PointExchangeOrder();
        order.setPtExcOrdSn(orderSn);
        order.setUsrSn(userSn);
        order.setUsrNm("홍길동");
        order.setPtExcOrdAmt(30_000);
        order.setPtExcOrdStatusCd(status.getCode());
        order.setPtExcOrdBankNm(bankName);
        order.setPtExcOrdAcntNo(accountNo);
        order.setPtExcOrdRegDt(LocalDateTime.of(2026, 8, 5, 10, 0));
        return order;
    }
}
