package nct.ops.notice.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.notice.domain.Notice;
import nct.ops.notice.dto.PublicNoticeDetailResponse;
import nct.ops.notice.dto.PublicNoticeListItemResponse;
import nct.ops.notice.dto.PublicNoticePageResponse;
import nct.ops.notice.dto.PublicNoticeTypeResponse;
import nct.ops.notice.mapper.NoticeMapper;
import nct.ops.reference.service.ReferenceDataService;

/**
 * F-COM-013 공지사항 공개 조회 기능이다.
 *
 * <p>방문자도 호출할 수 있으므로 요청한 유형 코드와 페이지 범위를 서버에서 검증한다.
 * 공개 상태·기간 필터는 Mapper에도 고정해, 화면에서 숨긴 값이 API로 노출되지 않게 한다.</p>
 */
@Service
@RequiredArgsConstructor
public class PublicNoticeService {

    private static final String NOTICE_TYPE_GROUP = "NTCG01";
    private static final int MAX_PAGE_SIZE = 50;
    private static final int SUMMARY_LENGTH = 120;

    private final NoticeMapper noticeMapper;
    private final ReferenceDataService referenceDataService;

    /** 프론트 필터가 코드값을 복제하지 않도록 활성 공지 유형을 공통코드에서 제공한다. */
    @Transactional(readOnly = true)
    public List<PublicNoticeTypeResponse> getPublicNoticeTypes() {
        return referenceDataService.getActiveCodes(NOTICE_TYPE_GROUP).stream()
                .map(code -> PublicNoticeTypeResponse.builder()
                        .code(code.getCode())
                        .name(code.getName())
                        .build())
                .toList();
    }

    /** 게시 가능한 공지 목록을 상단 고정·게시일 역순으로 반환한다. */
    @Transactional(readOnly = true)
    public PublicNoticePageResponse getPublicNotices(String typeCode, int page, int size) {
        validatePage(page, size);

        String normalizedTypeCode = normalizeOptional(typeCode);
        if (normalizedTypeCode != null
                && !referenceDataService.isActiveCode(NOTICE_TYPE_GROUP, normalizedTypeCode)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        long totalItems = noticeMapper.countPublicNotices(normalizedTypeCode);
        long offset = ((long) page - 1L) * size;
        List<PublicNoticeListItemResponse> items = offset >= totalItems
                ? List.of()
                : noticeMapper.findPublicNotices(normalizedTypeCode, offset, size)
                        .stream()
                        .map(this::toListItem)
                        .toList();

        int totalPages = totalItems == 0 ? 0 : (int) ((totalItems + size - 1) / size);
        return PublicNoticePageResponse.builder()
                .items(List.copyOf(items))
                .page(page)
                .size(size)
                .totalItems(totalItems)
                .totalPages(totalPages)
                .build();
    }

    /**
     * 공개 조건을 만족하는 공지 상세를 반환한다.
     * 조회수 증가는 정본에 확정 규칙이 없으므로 이 읽기 기능에서는 수행하지 않는다.
     */
    @Transactional(readOnly = true)
    public PublicNoticeDetailResponse getPublicNotice(Long noticeId) {
        if (noticeId == null || noticeId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Notice notice = noticeMapper.findPublicNoticeById(noticeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        return PublicNoticeDetailResponse.builder()
                .id(notice.getNoticeSn())
                .typeCode(notice.getTypeCode())
                .typeName(notice.getTypeName())
                .title(notice.getTitle())
                .content(notice.getContent())
                .pinned(isYes(notice.getPinnedYn()))
                .viewCount(notice.getViewCount())
                .publishedAt(notice.getPostingStartAt() != null
                        ? notice.getPostingStartAt()
                        : notice.getRegisteredAt())
                .build();
    }

    private PublicNoticeListItemResponse toListItem(Notice notice) {
        return PublicNoticeListItemResponse.builder()
                .id(notice.getNoticeSn())
                .typeCode(notice.getTypeCode())
                .typeName(notice.getTypeName())
                .title(notice.getTitle())
                .summary(toSummary(notice.getContent()))
                .pinned(isYes(notice.getPinnedYn()))
                .viewCount(notice.getViewCount())
                .publishedAt(notice.getPostingStartAt() != null
                        ? notice.getPostingStartAt()
                        : notice.getRegisteredAt())
                .build();
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isYes(String value) {
        return "Y".equals(value);
    }

    private String toSummary(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= SUMMARY_LENGTH
                ? normalized
                : normalized.substring(0, SUMMARY_LENGTH) + "…";
    }
}
