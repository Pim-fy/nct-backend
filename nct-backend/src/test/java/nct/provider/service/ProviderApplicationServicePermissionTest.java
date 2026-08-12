package nct.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.member.port.AdminMemberIdentityReader;
import nct.notification.service.NotificationService;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.reference.service.ReferenceDataService;
import nct.provider.dto.ProviderApplicationRequest;
import nct.provider.dto.ProviderApplicationResponse;
import nct.provider.mapper.ProviderApplicationMapper;

// @ai_generated F-PROV-015: 모드 진입용 "활성 권한 하나 이상" 읽기 계약을 단위 검증한다.
class ProviderApplicationServicePermissionTest {

    private final ProviderApplicationMapper mapper = mock(ProviderApplicationMapper.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AuditLogPort auditLogPort = mock(AuditLogPort.class);
    private final ProviderApplicationService service = new ProviderApplicationService(
            mapper,
            mock(ReferenceDataService.class),
            mock(FileStorageService.class),
            mock(AdminMemberIdentityReader.class),
            notificationService,
            auditLogPort);

    @Test
    void 활성_제공자_권한이_하나라도_있으면_통과한다() {
        when(mapper.hasAnyActivePermission(101L)).thenReturn(true);

        assertThatCode(() -> service.requireAnyActivePermission(101L)).doesNotThrowAnyException();
    }

    @Test
    void 활성_제공자_권한이_없으면_차단한다() {
        when(mapper.hasAnyActivePermission(101L)).thenReturn(false);

        assertThatThrownBy(() -> service.requireAnyActivePermission(101L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 이메일_미인증_사용자는_제공자_신청을_할_수_없다() {
        ProviderApplicationRequest request = new ProviderApplicationRequest();
        request.setCategorySns(List.of(30L));
        when(mapper.isEmailCertified(101L)).thenReturn(false);

        assertThatThrownBy(() -> service.apply(101L, request))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_NOT_VERIFIED);

        verify(mapper).isEmailCertified(101L);
        verify(mapper, never()).insertApplication(any());
    }

    @Test
    void 제공자_신청이_승인되면_승인_알림을_보낸다() {
        ProviderApplicationResponse application = new ProviderApplicationResponse();
        application.setApplicationSn(500L);
        application.setUserSn(101L);
        application.setCategorySn(30L);
        application.setStatusCode("PRVC0002");
        ProviderApplicationResponse updated = new ProviderApplicationResponse();
        updated.setApplicationSn(500L);
        updated.setUserSn(101L);
        updated.setCategorySn(30L);
        updated.setStatusCode("PRVC0003");
        when(mapper.findForUpdate(500L))
                .thenReturn(Optional.of(application), Optional.of(updated));
        when(mapper.changeApplicationStatus(500L, "PRVC0003", null, "9")).thenReturn(1);
        when(mapper.insertStatus(500L, "PRVC0017", "승인 사유", "9")).thenReturn(1);
        when(mapper.insertActivePermission(101L, 30L, 500L, "9")).thenReturn(1);
        when(mapper.findFilesByApplicationSn(500L)).thenReturn(List.of());

        service.approve(500L, "승인 사유", 9L);

        verify(notificationService).notifyProviderApprovalResult(101L, true, null);
        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().actionCode()).isEqualTo("ADMIN_APPROVE");
        assertThat(auditCaptor.getValue().referenceTypeCode()).isEqualTo("REFC0015");
        assertThat(auditCaptor.getValue().referenceSn()).isEqualTo(500L);
        assertThat(auditCaptor.getValue().relatedReferenceTypeCode()).isEqualTo("REFC0001");
        assertThat(auditCaptor.getValue().relatedReferenceSn()).isEqualTo(101L);
    }

    @Test
    void 제공자_신청이_반려되면_반려_알림을_보낸다() {
        ProviderApplicationResponse application = new ProviderApplicationResponse();
        application.setApplicationSn(501L);
        application.setUserSn(102L);
        application.setCategorySn(31L);
        application.setStatusCode("PRVC0002");
        ProviderApplicationResponse updated = new ProviderApplicationResponse();
        updated.setApplicationSn(501L);
        updated.setUserSn(102L);
        updated.setCategorySn(31L);
        updated.setStatusCode("PRVC0004");
        when(mapper.findForUpdate(501L))
                .thenReturn(Optional.of(application), Optional.of(updated));
        when(mapper.changeApplicationStatus(501L, "PRVC0004", "자격 미달", "9")).thenReturn(1);
        when(mapper.insertStatus(501L, "PRVC0018", "자격 미달", "9")).thenReturn(1);
        when(mapper.findFilesByApplicationSn(501L)).thenReturn(List.of());

        service.reject(501L, "자격 미달", 9L);

        verify(notificationService).notifyProviderApprovalResult(102L, false, "자격 미달");
        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().actionCode()).isEqualTo("ADMIN_REJECT");
        assertThat(auditCaptor.getValue().referenceTypeCode()).isEqualTo("REFC0015");
        assertThat(auditCaptor.getValue().relatedReferenceSn()).isEqualTo(102L);
    }
}
