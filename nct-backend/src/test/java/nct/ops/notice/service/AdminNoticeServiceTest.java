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
        service = new AdminNoticeService(noticeMapper, referenceDataService, changeHistoryPort);
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
        assertThat(auditCaptor.getValue().getAfterSummary()).doesNotContain("공지 본문");
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

        assertThat(service.hideNotice(9L, "게시 종료", 7L).getStatusCode())
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

        assertThat(service.hideNotice(9L, "게시 종료", 7L).getStatusCode())
                .isEqualTo("NTCC0007");

        verify(changeHistoryPort).record(any(NoticeChangeHistoryCommand.class));
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
        assertThatThrownBy(() -> service.hideNotice(1L, "숨김", null))
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
        request.setChangeReason("점검 일정 안내");
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
