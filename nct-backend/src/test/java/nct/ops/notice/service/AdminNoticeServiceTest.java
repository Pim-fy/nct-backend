package nct.ops.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
        assertThat(writeCaptor.getValue().getPostingStartAt())
                .isEqualTo(request.getPostingStartAt());
        assertThat(writeCaptor.getValue().getPostingEndAt())
                .isEqualTo(request.getPostingEndAt());

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
    void rollsBackCreateWhenStoredPostingPeriodDiffersFromRequest() {
        AdminNoticeUpsertRequest request = validRequest();
        Notice incorrectlyStored = Notice.builder()
                .noticeSn(41L)
                .writerUserSn(7L)
                .typeCode("NTCC0003")
                .statusCode("NTCC0006")
                .title(request.getTitle())
                .content(request.getContent())
                .postingStartAt(LocalDateTime.of(2026, 7, 16, 10, 0))
                .postingEndAt(null)
                .pinnedYn("Y")
                .viewCount(0L)
                .useYn("Y")
                .registeredAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(noticeMapper.insertAdminNotice(any(AdminNoticeWriteCommand.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(0, AdminNoticeWriteCommand.class).setNoticeSn(41L);
                    return 1;
                });
        when(noticeMapper.findAdminNoticeById(41L)).thenReturn(Optional.of(incorrectlyStored));

        assertThatThrownBy(() -> service.createNotice(request, 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DATABASE_ERROR);
        verifyNoInteractions(changeHistoryPort);
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

    /** 담당자 7 | ISSUE-T7-002: 같은 토큰을 순차 재사용하면 첫 수정만 반영되는지 확인한다. */
    @Test
    void acceptsFirstUpdateAndRejectsSequentialReuseOfSameRevision() {
        LocalDateTime originalRevisionAt = LocalDateTime.of(2026, 8, 17, 18, 36, 32);
        Notice original = notice(
                15L, "NTCC0006", "Y", originalRevisionAt, "수정 전 제목", "수정 전 본문");
        Notice updated = notice(15L, "NTCC0006", "Y", originalRevisionAt.plusSeconds(1));
        when(noticeMapper.findAdminNoticeById(15L))
                .thenReturn(Optional.of(original))
                .thenReturn(Optional.of(updated));

        AdminNoticeUpsertRequest request = validRequest();
        request.setExpectedUpdatedAt(originalRevisionAt);
        request.setExpectedRevision(service.getNotice(15L).getRevisionToken());
        when(noticeMapper.findAdminNoticeByIdForUpdate(15L))
                .thenReturn(Optional.of(original))
                .thenReturn(Optional.of(updated));
        when(noticeMapper.updateAdminNotice(any(AdminNoticeWriteCommand.class))).thenReturn(1);

        assertThat(service.updateNotice(15L, request, 7L))
                .satisfies(response -> {
                    assertThat(response.getTitle()).isEqualTo(request.getTitle());
                    assertThat(response.getContent()).isEqualTo(request.getContent());
                    assertThat(response.getUpdatedAt()).isEqualTo(originalRevisionAt.plusSeconds(1));
                    assertThat(response.getRevisionToken()).isNotEqualTo(request.getExpectedRevision());
                });
        assertThatThrownBy(() -> service.updateNotice(15L, request, 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(noticeMapper, times(1)).updateAdminNotice(any(AdminNoticeWriteCommand.class));
        verify(changeHistoryPort, times(1)).record(any(NoticeChangeHistoryCommand.class));
    }

    /** 담당자 7 | ISSUE-T7-002: UPDATE 영향 행이 없으면 감사 이력 없이 충돌로 끝낸다. */
    @Test
    void rejectsUpdateWhenAtomicRevisionConditionMatchesNoRow() {
        LocalDateTime originalRevisionAt = LocalDateTime.of(2026, 8, 17, 18, 36, 32);
        Notice original = notice(15L, "NTCC0006", "Y", originalRevisionAt);
        when(noticeMapper.findAdminNoticeById(15L)).thenReturn(Optional.of(original));

        AdminNoticeUpsertRequest request = validRequest();
        request.setExpectedUpdatedAt(originalRevisionAt);
        request.setExpectedRevision(service.getNotice(15L).getRevisionToken());
        when(noticeMapper.findAdminNoticeByIdForUpdate(15L)).thenReturn(Optional.of(original));
        when(noticeMapper.updateAdminNotice(any(AdminNoticeWriteCommand.class))).thenReturn(0);

        assertThatThrownBy(() -> service.updateNotice(15L, request, 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verifyNoInteractions(changeHistoryPort);
    }

    /** 담당자 7 | ISSUE-T7-002: 동시 수정도 원자적 리비전 조건으로 한 건만 성공해야 한다. */
    @Test
    void concurrentUpdatesWithSameRevisionAllowOnlyOneSuccess() throws Exception {
        LocalDateTime originalRevisionAt = LocalDateTime.of(2026, 8, 17, 18, 36, 32);
        Notice original = notice(
                15L, "NTCC0006", "Y", originalRevisionAt, "수정 전 제목", "수정 전 본문");
        AtomicReference<Notice> storedNotice = new AtomicReference<>(original);
        when(noticeMapper.findAdminNoticeById(15L))
                .thenAnswer(invocation -> Optional.of(storedNotice.get()));

        AdminNoticeUpsertRequest request = validRequest();
        request.setExpectedUpdatedAt(originalRevisionAt);
        request.setExpectedRevision(service.getNotice(15L).getRevisionToken());
        when(noticeMapper.findAdminNoticeByIdForUpdate(15L))
                .thenAnswer(invocation -> Optional.of(storedNotice.get()));
        when(noticeMapper.updateAdminNotice(any(AdminNoticeWriteCommand.class)))
                .thenAnswer(invocation -> {
                    AdminNoticeWriteCommand command = invocation.getArgument(
                            0, AdminNoticeWriteCommand.class);
                    Notice current = storedNotice.get();
                    if (!command.getExpectedUpdatedAt().equals(current.getUpdatedAt())) {
                        return 0;
                    }
                    Notice next = noticeFromCommand(
                            current, command, command.getExpectedUpdatedAt().plusSeconds(1));
                    return storedNotice.compareAndSet(current, next) ? 1 : 0;
                });

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ErrorCode> first = executor.submit(() -> updateAfterSignal(start, request));
            Future<ErrorCode> second = executor.submit(() -> updateAfterSignal(start, request));
            start.countDown();

            ErrorCode firstResult = first.get(5, TimeUnit.SECONDS);
            ErrorCode secondResult = second.get(5, TimeUnit.SECONDS);
            int successCount = (firstResult == null ? 1 : 0) + (secondResult == null ? 1 : 0);
            int conflictCount = (firstResult == ErrorCode.CONFLICT ? 1 : 0)
                    + (secondResult == ErrorCode.CONFLICT ? 1 : 0);

            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);
            assertThat(storedNotice.get().getTitle()).isEqualTo(request.getTitle());
            assertThat(storedNotice.get().getContent()).isEqualTo(request.getContent());
            assertThat(storedNotice.get().getUpdatedAt())
                    .isEqualTo(originalRevisionAt.plusSeconds(1));
            verify(changeHistoryPort, times(1)).record(any(NoticeChangeHistoryCommand.class));
        } finally {
            executor.shutdownNow();
        }
    }

    /** 담당자 7 | ISSUE-T7-002: Mapper가 같은 초에도 갱신시각을 전진시키는 계약을 고정한다. */
    @Test
    void mapperUpdateAdvancesTimestampAndKeepsExpectedTimestampCondition() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "mapper/ops/notice/NoticeMapper.xml")) {
            assertThat(input).isNotNull();
            String mapperXml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(mapperXml)
                    .contains("NTC_UPDT_DT = GREATEST(")
                    .contains("DATE_ADD(#{expectedUpdatedAt}, INTERVAL 1 SECOND)")
                    .contains("AND NTC_UPDT_DT = #{expectedUpdatedAt}");
        }
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

    private ErrorCode updateAfterSignal(CountDownLatch start,
                                        AdminNoticeUpsertRequest request) throws InterruptedException {
        start.await();
        try {
            service.updateNotice(15L, request, 7L);
            return null;
        } catch (CustomException exception) {
            return exception.getErrorCode();
        }
    }

    private Notice notice(Long noticeId, String statusCode, String useYn) {
        return notice(noticeId, statusCode, useYn, LocalDateTime.now());
    }

    private Notice notice(Long noticeId, String statusCode, String useYn,
                           LocalDateTime updatedAt) {
        return notice(
                noticeId, statusCode, useYn, updatedAt,
                "서비스 점검 안내", "공지 본문입니다.");
    }

    private Notice notice(Long noticeId, String statusCode, String useYn,
                          LocalDateTime updatedAt, String title, String content) {
        return Notice.builder()
                .noticeSn(noticeId)
                .writerUserSn(7L)
                .writerName("관리자")
                .typeCode("NTCC0003")
                .typeName("안내")
                .statusCode(statusCode)
                .statusName("NTCC0007".equals(statusCode) ? "숨김" : "게시")
                .title(title)
                .content(content)
                .postingStartAt(LocalDateTime.of(2026, 7, 16, 9, 0))
                .postingEndAt(LocalDateTime.of(2026, 7, 20, 9, 0))
                .pinnedYn("Y")
                .viewCount(3L)
                .useYn(useYn)
                .registeredAt(LocalDateTime.now().minusDays(2))
                .updatedAt(updatedAt)
                .build();
    }

    private Notice noticeFromCommand(Notice current, AdminNoticeWriteCommand command,
                                     LocalDateTime updatedAt) {
        return Notice.builder()
                .noticeSn(current.getNoticeSn())
                .writerUserSn(current.getWriterUserSn())
                .writerName(current.getWriterName())
                .typeCode(command.getTypeCode())
                .typeName(current.getTypeName())
                .statusCode(command.getStatusCode())
                .statusName(current.getStatusName())
                .title(command.getTitle())
                .content(command.getContent())
                .postingStartAt(command.getPostingStartAt())
                .postingEndAt(command.getPostingEndAt())
                .pinnedYn(command.getPinnedYn())
                .viewCount(current.getViewCount())
                .useYn(current.getUseYn())
                .registeredAt(current.getRegisteredAt())
                .updatedAt(updatedAt)
                .updaterActorId(command.getActorId())
                .build();
    }
}
