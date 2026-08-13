package nct.ops.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.notice.domain.AdminNoticeWriteCommand;
import nct.ops.notice.domain.Notice;
import nct.ops.notice.dto.AdminNoticeUpsertRequest;
import nct.ops.notice.mapper.NoticeMapper;
import nct.ops.notice.port.NoticeChangeHistoryCommand;
import nct.ops.notice.port.NoticeChangeHistoryPort;
import nct.ops.reference.service.ReferenceDataService;
import nct.member.port.AdminMemberIdentityReader;
import nct.audit.service.AuditLogService;

/** F-OPS-023 관리자 공지의 코드·기간·멱등·감사 경계를 확인한다. */
class AdminNoticeServiceTest {

    private NoticeMapper noticeMapper;
    private ReferenceDataService referenceDataService;
    private NoticeChangeHistoryPort changeHistoryPort;
    private AdminNoticeService service;

    @BeforeEach
    void setUp() {
        noticeMapper = mock(NoticeMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        changeHistoryPort = mock(NoticeChangeHistoryPort.class);
        service = new AdminNoticeService(
                noticeMapper,
                referenceDataService,
                changeHistoryPort,
                mock(AdminMemberIdentityReader.class),
                mock(AuditLogService.class));
    }

    @Test
    void createsNoticeWithAuthenticatedActorAndRecordsSafeSummary() {
        AdminNoticeUpsertRequest request = validRequest();
        Notice stored = notice(41L, "NTCC0006", "Y");
        when(noticeMapper.insertAdminNotice(any(AdminNoticeWriteCommand.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(0, AdminNoticeWriteCommand.class).setNoticeSn(41L);
                    return 1;
                });
        when(noticeMapper.findAdminNoticeById(41L)).thenReturn(Optional.of(stored));

        assertThat(service.createNotice(request, 7L).getNoticeId()).isEqualTo(41L);

        ArgumentCaptor<AdminNoticeWriteCommand> writeCaptor =
                ArgumentCaptor.forClass(AdminNoticeWriteCommand.class);
        verify(noticeMapper).insertAdminNotice(writeCaptor.capture());
        assertThat(writeCaptor.getValue().getWriterUserSn()).isEqualTo(7L);
        assertThat(writeCaptor.getValue().getActorId()).isEqualTo("USR:7");

        ArgumentCaptor<NoticeChangeHistoryCommand> auditCaptor =
                ArgumentCaptor.forClass(NoticeChangeHistoryCommand.class);
        verify(changeHistoryPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("CREATE");
        assertThat(auditCaptor.getValue().getReason()).isEqualTo("공지 등록");
        assertThat(auditCaptor.getValue().getAfterSummary()).doesNotContain("공지 본문");
    }

    @Test
    void keepsAutomaticActionNameWhenLegacyClientSendsMemo() {
        AdminNoticeUpsertRequest request = validRequest();
        request.setChangeReason("정기 점검 메모");
        Notice stored = notice(41L, "NTCC0006", "Y");
        when(noticeMapper.insertAdminNotice(any(AdminNoticeWriteCommand.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(0, AdminNoticeWriteCommand.class).setNoticeSn(41L);
                    return 1;
                });
        when(noticeMapper.findAdminNoticeById(41L)).thenReturn(Optional.of(stored));

        service.createNotice(request, 7L);

        ArgumentCaptor<NoticeChangeHistoryCommand> auditCaptor =
                ArgumentCaptor.forClass(NoticeChangeHistoryCommand.class);
        verify(changeHistoryPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getReason())
                .isEqualTo("공지 등록: 정기 점검 메모");
    }

    @Test
    void rejectsReversedPostingPeriodBeforeWriting() {
        AdminNoticeUpsertRequest request = validRequest();
        request.setPostingStartAt(LocalDateTime.of(2026, 7, 20, 9, 0));
        request.setPostingEndAt(LocalDateTime.of(2026, 7, 19, 9, 0));

        assertThatThrownBy(() -> service.createNotice(request, 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(noticeMapper, never()).insertAdminNotice(any(AdminNoticeWriteCommand.class));
        verifyNoInteractions(changeHistoryPort);
    }

    @Test
    void repeatedHideDoesNotWriteOrDuplicateHistory() {
        Notice hidden = notice(9L, "NTCC0007", "Y");
        when(noticeMapper.findAdminNoticeByIdForUpdate(9L)).thenReturn(Optional.of(hidden));

        assertThat(service.hideNotice(9L, null, 7L).getStatusCode())
                .isEqualTo("NTCC0007");

        verify(noticeMapper, never()).hideAdminNotice(any(Long.class), any(String.class));
        verifyNoInteractions(changeHistoryPort);
    }

    @Test
    void hideLocksCurrentRowAndRecordsOnlyChangedResult() {
        Notice before = notice(9L, "NTCC0006", "Y");
        Notice hidden = notice(9L, "NTCC0007", "Y");
        when(noticeMapper.findAdminNoticeByIdForUpdate(9L)).thenReturn(Optional.of(before));
        when(noticeMapper.hideAdminNotice(9L, "USR:7")).thenReturn(1);
        when(noticeMapper.findAdminNoticeById(9L)).thenReturn(Optional.of(hidden));

        assertThat(service.hideNotice(9L, null, 7L).getStatusCode())
                .isEqualTo("NTCC0007");

        ArgumentCaptor<NoticeChangeHistoryCommand> auditCaptor =
                ArgumentCaptor.forClass(NoticeChangeHistoryCommand.class);
        verify(changeHistoryPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getReason()).isEqualTo("공지 숨김");
    }

    @Test
    void repeatedPublishDoesNotWriteOrDuplicateHistory() {
        Notice published = notice(10L, "NTCC0006", "Y");
        when(noticeMapper.findAdminNoticeByIdForUpdate(10L)).thenReturn(Optional.of(published));

        assertThat(service.publishNotice(10L, null, "0".repeat(64), 7L).getStatusCode())
                .isEqualTo("NTCC0006");

        verify(noticeMapper, never()).publishAdminNotice(any(Long.class), any(String.class));
        verifyNoInteractions(changeHistoryPort);
    }

    @Test
    void publishLocksCurrentRowAndRecordsStatusChange() {
        Notice before = notice(10L, "NTCC0007", "Y");
        Notice published = notice(10L, "NTCC0006", "Y");
        when(noticeMapper.findAdminNoticeById(10L))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(published));
        String expectedRevision = service.getNotice(10L).getRevisionToken();
        when(noticeMapper.findAdminNoticeByIdForUpdate(10L)).thenReturn(Optional.of(before));
        when(noticeMapper.publishAdminNotice(10L, "USR:7")).thenReturn(1);

        assertThat(service.publishNotice(10L, null, expectedRevision, 7L).getStatusCode())
                .isEqualTo("NTCC0006");

        ArgumentCaptor<NoticeChangeHistoryCommand> auditCaptor =
                ArgumentCaptor.forClass(NoticeChangeHistoryCommand.class);
        verify(changeHistoryPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("PUBLISH");
        assertThat(auditCaptor.getValue().getReason()).isEqualTo("공지 게시");
    }

    @Test
    void rejectsPublishStartedBeforeAnotherAdministratorChangedNotice() {
        LocalDateTime sameSecond = LocalDateTime.of(2026, 7, 30, 17, 0);
        Notice originalDraft = notice(10L, "NTCC0005", "Y", sameSecond);
        Notice currentlyHidden = notice(10L, "NTCC0007", "Y", sameSecond);
        when(noticeMapper.findAdminNoticeById(10L)).thenReturn(Optional.of(originalDraft));
        String staleRevision = service.getNotice(10L).getRevisionToken();
        when(noticeMapper.findAdminNoticeByIdForUpdate(10L)).thenReturn(Optional.of(currentlyHidden));

        assertThatThrownBy(() ->
                service.publishNotice(10L, null, staleRevision, 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(noticeMapper, never()).publishAdminNotice(any(Long.class), any(String.class));
        verifyNoInteractions(changeHistoryPort);
    }

    @Test
    void deleteUsesSoftDeleteAndRecordsReason() {
        Notice before = notice(12L, "NTCC0006", "Y");
        when(noticeMapper.findAdminNoticeByIdForUpdate(12L)).thenReturn(Optional.of(before));
        when(noticeMapper.softDeleteAdminNotice(12L, "USR:7")).thenReturn(1);

        service.deleteNotice(12L, "중복 공지 정리", 7L);

        verify(noticeMapper).softDeleteAdminNotice(12L, "USR:7");
        ArgumentCaptor<NoticeChangeHistoryCommand> auditCaptor =
                ArgumentCaptor.forClass(NoticeChangeHistoryCommand.class);
        verify(changeHistoryPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("DELETE");
        assertThat(auditCaptor.getValue().getAfterSummary()).endsWith("use=N");
    }

    @Test
    void repeatedDeleteDoesNotWriteOrDuplicateHistory() {
        when(noticeMapper.findAdminNoticeByIdForUpdate(12L))
                .thenReturn(Optional.of(notice(12L, "NTCC0007", "N")));

        service.deleteNotice(12L, "중복 공지 정리", 7L);

        verify(noticeMapper, never()).softDeleteAdminNotice(any(Long.class), any(String.class));
        verifyNoInteractions(changeHistoryPort);
    }

    @Test
    void rejectsDeleteWithoutReasonBeforeWriting() {
        assertThatThrownBy(() -> service.deleteNotice(12L, " ", 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(noticeMapper, referenceDataService, changeHistoryPort);
    }

    @Test
    void rejectsSameSecondStaleUpdateThatCouldRestorePreviouslyHiddenStatus() {
        LocalDateTime sameSecond = LocalDateTime.of(2026, 7, 16, 10, 30, 0);
        Notice originallyPublished = notice(15L, "NTCC0006", "Y", sameSecond);
        Notice currentlyHidden = notice(15L, "NTCC0007", "Y", sameSecond);
        when(noticeMapper.findAdminNoticeById(15L)).thenReturn(Optional.of(originallyPublished));

        AdminNoticeUpsertRequest staleRequest = validRequest();
        staleRequest.setExpectedUpdatedAt(sameSecond);
        staleRequest.setExpectedRevision(service.getNotice(15L).getRevisionToken());
        when(noticeMapper.findAdminNoticeByIdForUpdate(15L)).thenReturn(Optional.of(currentlyHidden));

        assertThatThrownBy(() -> service.updateNotice(15L, staleRequest, 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(noticeMapper, never()).updateAdminNotice(any(AdminNoticeWriteCommand.class));
        verifyNoInteractions(changeHistoryPort);
    }

    @Test
    void rejectsMissingActorBeforeReadingNotice() {
        assertThatThrownBy(() -> service.hideNotice(1L, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(noticeMapper, referenceDataService, changeHistoryPort);
    }

    private AdminNoticeUpsertRequest validRequest() {
        AdminNoticeUpsertRequest request = new AdminNoticeUpsertRequest();
        request.setTypeCode("NTCC0003");
        request.setStatusCode("NTCC0006");
        request.setTitle("서비스 점검 안내");
        request.setContent("공지 본문입니다.");
        request.setPostingStartAt(LocalDateTime.of(2026, 7, 16, 9, 0));
        request.setPostingEndAt(LocalDateTime.of(2026, 7, 20, 9, 0));
        request.setPinned(Boolean.TRUE);
        return request;
    }

    private Notice notice(Long noticeId, String statusCode, String useYn) {
        return notice(noticeId, statusCode, useYn, LocalDateTime.now());
    }

    private Notice notice(Long noticeId, String statusCode, String useYn,
                          LocalDateTime updatedAt) {
        return Notice.builder()
                .noticeSn(noticeId)
                .writerUserSn(7L)
                .writerName("관리자")
                .typeCode("NTCC0003")
                .typeName("안내")
                .statusCode(statusCode)
                .statusName("NTCC0007".equals(statusCode) ? "숨김" : "게시")
                .title("서비스 점검 안내")
                .content("공지 본문입니다.")
                .postingStartAt(LocalDateTime.now().minusDays(1))
                .postingEndAt(LocalDateTime.now().plusDays(1))
                .pinnedYn("Y")
                .viewCount(3L)
                .useYn(useYn)
                .registeredAt(LocalDateTime.now().minusDays(2))
                .updatedAt(updatedAt)
                .build();
    }
}
