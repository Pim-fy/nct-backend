package nct.ops.notice.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.notice.domain.AdminNoticeWriteCommand;
import nct.ops.notice.domain.Notice;
import nct.ops.notice.dto.AdminNoticeCodeResponse;
import nct.ops.notice.dto.AdminNoticeDetailResponse;
import nct.ops.notice.dto.AdminNoticeListItemResponse;
import nct.ops.notice.dto.AdminNoticeOptionsResponse;
import nct.ops.notice.dto.AdminNoticePageResponse;
import nct.ops.notice.dto.AdminNoticeUpsertRequest;
import nct.ops.notice.mapper.NoticeMapper;
import nct.ops.notice.port.NoticeChangeHistoryCommand;
import nct.ops.notice.port.NoticeChangeHistoryPort;
import nct.ops.reference.domain.CommonCode;
import nct.ops.reference.service.ReferenceDataService;

/**
 * F-OPS-023 관리자 공지 등록·수정·숨김·소프트 삭제 흐름이다.
 *
 * <p>유형·상태 코드는 공통코드 계약으로 검증하고, 공개 여부는 사용 여부·게시 상태·
 * 노출 기간을 함께 계산한다. 관리자 요청이어도 화면 입력을 신뢰하지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminNoticeService {

    private static final String NOTICE_TYPE_GROUP = "NTCG01";
    private static final String NOTICE_STATUS_GROUP = "NTCG02";
    private static final String PUBLISHED_STATUS = "NTCC0006";
    private static final String HIDDEN_STATUS = "NTCC0007";
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final NoticeMapper noticeMapper;
    private final ReferenceDataService referenceDataService;
    private final NoticeChangeHistoryPort changeHistoryPort;

    @Transactional(readOnly = true)
    public AdminNoticeOptionsResponse getOptions() {
        return AdminNoticeOptionsResponse.builder()
                .types(toCodeResponses(referenceDataService.getActiveCodes(NOTICE_TYPE_GROUP)))
                .statuses(toCodeResponses(referenceDataService.getActiveCodes(NOTICE_STATUS_GROUP)))
                .build();
    }

    @Transactional(readOnly = true)
    public AdminNoticePageResponse getNotices(String typeCode, String statusCode, String keyword,
                                              int page, int size) {
        validatePage(page, size);
        String normalizedType = normalizeOptional(typeCode);
        String normalizedStatus = normalizeOptional(statusCode);
        String normalizedKeyword = normalizeOptional(keyword);
        if (normalizedType != null) {
            referenceDataService.requireActiveCode(NOTICE_TYPE_GROUP, normalizedType);
        }
        if (normalizedStatus != null) {
            referenceDataService.requireActiveCode(NOTICE_STATUS_GROUP, normalizedStatus);
        }
        if (normalizedKeyword != null && normalizedKeyword.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        long totalItems = noticeMapper.countAdminNotices(
                normalizedType, normalizedStatus, normalizedKeyword);
        long offset = (long) (page - 1) * size;
        List<AdminNoticeListItemResponse> items = offset >= totalItems
                ? List.of()
                : noticeMapper.findAdminNotices(
                        normalizedType, normalizedStatus, normalizedKeyword, offset, size)
                    .stream()
                    .map(this::toListItem)
                    .toList();

        return AdminNoticePageResponse.builder()
                .items(items)
                .page(page)
                .size(size)
                .totalItems(totalItems)
                .totalPages(totalItems == 0 ? 0 : (int) ((totalItems + size - 1) / size))
                .build();
    }

    @Transactional(readOnly = true)
    public AdminNoticeDetailResponse getNotice(Long noticeId) {
        return toDetail(requireNotice(noticeId));
    }

    @Transactional
    public AdminNoticeDetailResponse createNotice(AdminNoticeUpsertRequest request, Long actorUserId) {
        validateActor(actorUserId);
        validateRequest(request);
        AdminNoticeWriteCommand command = toCommand(request, actorUserId, actorUserId, null);
        if (noticeMapper.insertAdminNotice(command) != 1 || command.getNoticeSn() == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        Notice created = requireNotice(command.getNoticeSn());
        recordChange("CREATE", actorUserId, created.getNoticeSn(), request.getChangeReason(),
                null, summary(created));
        return toDetail(created);
    }

    @Transactional
    public AdminNoticeDetailResponse updateNotice(Long noticeId, AdminNoticeUpsertRequest request,
                                                  Long actorUserId) {
        validateActor(actorUserId);
        validateRequest(request);
        Notice before = requireNoticeForUpdate(noticeId);
        if (request.getExpectedUpdatedAt() == null
                || !request.getExpectedUpdatedAt().equals(before.getUpdatedAt())
                || request.getExpectedRevision() == null
                || !request.getExpectedRevision().equals(revisionToken(before))) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        AdminNoticeWriteCommand command = toCommand(
                request, before.getWriterUserSn(), actorUserId, noticeId);
        if (noticeMapper.updateAdminNotice(command) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }

        Notice updated = requireNotice(noticeId);
        recordChange("UPDATE", actorUserId, noticeId, request.getChangeReason(),
                summary(before), summary(updated));
        return toDetail(updated);
    }

    @Transactional
    public AdminNoticeDetailResponse hideNotice(Long noticeId, String reason, Long actorUserId) {
        validateActor(actorUserId);
        validateReason(reason);
        Notice before = requireNoticeForUpdate(noticeId);
        if (!"Y".equals(before.getUseYn())) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
        if (HIDDEN_STATUS.equals(before.getStatusCode())) {
            return toDetail(before);
        }
        int changedRows = noticeMapper.hideAdminNotice(noticeId, actorId(actorUserId));
        if (changedRows != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        Notice hidden = requireNotice(noticeId);
        if (!HIDDEN_STATUS.equals(hidden.getStatusCode())) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
        recordChange("HIDE", actorUserId, noticeId, reason, summary(before), summary(hidden));
        return toDetail(hidden);
    }

    @Transactional
    public void deleteNotice(Long noticeId, String reason, Long actorUserId) {
        validateActor(actorUserId);
        validateReason(reason);
        Notice before = requireNoticeForUpdate(noticeId);
        if ("N".equals(before.getUseYn())) {
            return;
        }
        if (noticeMapper.softDeleteAdminNotice(noticeId, actorId(actorUserId)) != 1) {
            throw new CustomException(ErrorCode.CONFLICT);
        }
        recordChange("DELETE", actorUserId, noticeId, reason,
                summary(before), summary(before) + ",use=N");
    }

    private void validateRequest(AdminNoticeUpsertRequest request) {
        if (request == null
                || request.getTypeCode() == null
                || request.getStatusCode() == null
                || request.getTitle() == null
                || request.getContent() == null
                || request.getPinned() == null
                || request.getChangeReason() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        request.setTypeCode(request.getTypeCode().trim());
        request.setStatusCode(request.getStatusCode().trim());
        request.setTitle(request.getTitle().trim());
        request.setContent(request.getContent().trim());
        request.setChangeReason(request.getChangeReason().trim());
        if (request.getTypeCode().isBlank() || request.getTypeCode().length() > 30
                || request.getStatusCode().isBlank() || request.getStatusCode().length() > 30
                || request.getTitle().isBlank() || request.getTitle().length() > 200
                || request.getContent().isBlank() || request.getContent().length() > 4000
                || request.getChangeReason().isBlank() || request.getChangeReason().length() > 500) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        referenceDataService.requireActiveCode(NOTICE_TYPE_GROUP, request.getTypeCode());
        referenceDataService.requireActiveCode(NOTICE_STATUS_GROUP, request.getStatusCode());
        if (request.getPostingStartAt() != null && request.getPostingEndAt() != null
                && request.getPostingEndAt().isBefore(request.getPostingStartAt())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateActor(Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Notice requireNotice(Long noticeId) {
        validateNoticeId(noticeId);
        return noticeMapper.findAdminNoticeById(noticeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private void validateNoticeId(Long noticeId) {
        if (noticeId == null || noticeId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Notice requireNoticeForUpdate(Long noticeId) {
        validateNoticeId(noticeId);
        return noticeMapper.findAdminNoticeByIdForUpdate(noticeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private AdminNoticeWriteCommand toCommand(AdminNoticeUpsertRequest request,
                                              Long writerUserId, Long actorUserId, Long noticeId) {
        return AdminNoticeWriteCommand.builder()
                .noticeSn(noticeId)
                .writerUserSn(writerUserId)
                .typeCode(request.getTypeCode())
                .statusCode(request.getStatusCode())
                .title(request.getTitle())
                .content(request.getContent())
                .postingStartAt(request.getPostingStartAt())
                .postingEndAt(request.getPostingEndAt())
                .expectedUpdatedAt(request.getExpectedUpdatedAt())
                .pinnedYn(Boolean.TRUE.equals(request.getPinned()) ? "Y" : "N")
                .actorId(actorId(actorUserId))
                .build();
    }

    private List<AdminNoticeCodeResponse> toCodeResponses(List<CommonCode> codes) {
        return codes.stream()
                .map(code -> AdminNoticeCodeResponse.builder()
                        .code(code.getCode())
                        .name(code.getName())
                        .build())
                .toList();
    }

    private AdminNoticeListItemResponse toListItem(Notice notice) {
        return AdminNoticeListItemResponse.builder()
                .noticeId(notice.getNoticeSn())
                .typeCode(notice.getTypeCode())
                .typeName(notice.getTypeName())
                .statusCode(notice.getStatusCode())
                .statusName(notice.getStatusName())
                .title(notice.getTitle())
                .writerName(notice.getWriterName())
                .pinned("Y".equals(notice.getPinnedYn()))
                .viewCount(notice.getViewCount())
                .postingStartAt(notice.getPostingStartAt())
                .postingEndAt(notice.getPostingEndAt())
                .updatedAt(notice.getUpdatedAt())
                .visibleNow(isVisibleNow(notice))
                .build();
    }

    private AdminNoticeDetailResponse toDetail(Notice notice) {
        return AdminNoticeDetailResponse.builder()
                .noticeId(notice.getNoticeSn())
                .writerUserId(notice.getWriterUserSn())
                .writerName(notice.getWriterName())
                .typeCode(notice.getTypeCode())
                .typeName(notice.getTypeName())
                .statusCode(notice.getStatusCode())
                .statusName(notice.getStatusName())
                .title(notice.getTitle())
                .content(notice.getContent())
                .postingStartAt(notice.getPostingStartAt())
                .postingEndAt(notice.getPostingEndAt())
                .pinned("Y".equals(notice.getPinnedYn()))
                .viewCount(notice.getViewCount())
                .registeredAt(notice.getRegisteredAt())
                .updatedAt(notice.getUpdatedAt())
                .revisionToken(revisionToken(notice))
                .visibleNow(isVisibleNow(notice))
                .build();
    }

    /**
     * 정본 DB의 갱신 시각이 초 단위여도 같은 초에 바뀐 내용을 구분하기 위한 행 상태 토큰이다.
     * 비밀값이 아니라 동시 수정 감지용이며, 원문 대신 SHA-256 결과만 화면에 전달한다.
     */
    private String revisionToken(Notice notice) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateRevisionField(digest, notice.getNoticeSn());
            updateRevisionField(digest, notice.getWriterUserSn());
            updateRevisionField(digest, notice.getTypeCode());
            updateRevisionField(digest, notice.getStatusCode());
            updateRevisionField(digest, notice.getTitle());
            updateRevisionField(digest, notice.getContent());
            updateRevisionField(digest, notice.getPostingStartAt());
            updateRevisionField(digest, notice.getPostingEndAt());
            updateRevisionField(digest, notice.getPinnedYn());
            updateRevisionField(digest, notice.getUseYn());
            updateRevisionField(digest, notice.getUpdatedAt());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private void updateRevisionField(MessageDigest digest, Object value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private boolean isVisibleNow(Notice notice) {
        LocalDateTime now = LocalDateTime.now();
        return PUBLISHED_STATUS.equals(notice.getStatusCode())
                && "Y".equals(notice.getUseYn())
                && (notice.getPostingStartAt() == null || !notice.getPostingStartAt().isAfter(now))
                && (notice.getPostingEndAt() == null || !notice.getPostingEndAt().isBefore(now));
    }

    private String summary(Notice notice) {
        return "type=" + notice.getTypeCode()
                + ",status=" + notice.getStatusCode()
                + ",start=" + notice.getPostingStartAt()
                + ",end=" + notice.getPostingEndAt()
                + ",pinned=" + notice.getPinnedYn()
                + ",use=" + notice.getUseYn();
    }

    private void recordChange(String action, Long actorUserId, Long noticeId, String reason,
                              String before, String after) {
        changeHistoryPort.record(NoticeChangeHistoryCommand.builder()
                .action(action)
                .actorUserId(actorUserId)
                .noticeId(noticeId)
                .reason(reason.trim())
                .beforeSummary(before)
                .afterSummary(after)
                .build());
    }

    private String actorId(Long actorUserId) {
        return "USR:" + actorUserId;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
