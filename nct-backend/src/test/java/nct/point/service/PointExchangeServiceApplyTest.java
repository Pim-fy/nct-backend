package nct.point.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.audit.service.AuditLogService;
import nct.global.exception.ErrorCode;
import nct.global.security.crypto.FieldCryptoService;
import nct.notification.service.NotificationService;
import nct.point.domain.PointExchangeOrder;
import nct.point.dto.UserAccount;
import nct.point.exception.PointException;
import nct.point.mapper.PointExchangeOrderMapper;

/** 담당자 7 · F-PAY-012: 환전 신청 전에 복호화된 지급 계좌를 검증하는 회귀 테스트입니다. */
class PointExchangeServiceApplyTest {

    private PointExchangeOrderMapper exchangeMapper;
    private PointService pointService;
    private NotificationService notificationService;
    private AuditLogService auditLogService;
    private FieldCryptoService fieldCryptoService;
    private PointExchangeService service;

    @BeforeEach
    void setUp() {
        exchangeMapper = mock(PointExchangeOrderMapper.class);
        pointService = mock(PointService.class);
        notificationService = mock(NotificationService.class);
        auditLogService = mock(AuditLogService.class);
        fieldCryptoService = mock(FieldCryptoService.class);
        service = new PointExchangeService(
                exchangeMapper,
                pointService,
                notificationService,
                auditLogService,
                fieldCryptoService);
    }

    @Test
    void 암호화된_빈_계좌는_포인트_차감_전에_신청을_차단한다() {
        UserAccount account = new UserAccount();
        account.setBankNm("encrypted-empty-bank");
        account.setAcntNo("encrypted-empty-account");
        when(exchangeMapper.selectUserAccount(101L)).thenReturn(account);
        when(fieldCryptoService.decrypt("encrypted-empty-bank")).thenReturn("");
        when(fieldCryptoService.decrypt("encrypted-empty-account")).thenReturn("   ");

        assertThatThrownBy(() -> service.apply(101L, 10_000L))
                .isInstanceOf(PointException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXCHANGE_ACCOUNT_NOT_REGISTERED);

        verify(pointService, never()).debitExchange(anyLong(), anyLong(), anyString());
        verify(exchangeMapper, never()).insert(any(PointExchangeOrder.class));
        verifyNoInteractions(notificationService, auditLogService);
    }

    @Test
    void 유효한_복호화_계좌는_신청시점_스냅샷으로_저장한다() {
        UserAccount account = new UserAccount();
        account.setBankNm("encrypted-bank");
        account.setAcntNo("encrypted-account");
        when(exchangeMapper.selectUserAccount(101L)).thenReturn(account);
        when(fieldCryptoService.decrypt("encrypted-bank")).thenReturn("우리은행");
        when(fieldCryptoService.decrypt("encrypted-account")).thenReturn("123-456");
        when(pointService.debitExchange(101L, 10_000L, "환전 신청 차감")).thenReturn(77L);
        when(fieldCryptoService.encrypt("우리은행")).thenReturn("snapshot-bank");
        when(fieldCryptoService.encrypt("123-456")).thenReturn("snapshot-account");
        doAnswer(invocation -> {
            PointExchangeOrder order = invocation.getArgument(0);
            order.setPtExcOrdSn(88L);
            return 1;
        }).when(exchangeMapper).insert(any(PointExchangeOrder.class));

        service.apply(101L, 10_000L);

        verify(exchangeMapper).insert(argThat(order ->
                order.getUsrSn() == 101L
                        && order.getPtExcOrdAmt() == 10_000L
                        && order.getPtExcOrdDeductLdgSn() == 77L
                        && "snapshot-bank".equals(order.getPtExcOrdBankNm())
                        && "snapshot-account".equals(order.getPtExcOrdAcntNo())));
        verify(notificationService).notifyExchangeRequest(101L, 10_000L);
    }
}
