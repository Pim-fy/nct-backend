package nct.ops.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.notice.domain.Notice;
import nct.ops.notice.dto.PublicNoticePageResponse;
import nct.ops.notice.mapper.NoticeMapper;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.reference.domain.CommonCode;

/** F-COM-013 공개 공지의 필터·페이지·상세 실패 경로를 확인한다. */
class PublicNoticeServiceTest {

    private NoticeMapper noticeMapper;
    private ReferenceDataService referenceDataService;
    private PublicNoticeService service;

    @BeforeEach
    void setUp() {
        noticeMapper = mock(NoticeMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        service = new PublicNoticeService(noticeMapper, referenceDataService);
    }

    @Test
    void returnsPublicNoticePageWithOneBasedPagination() {
        LocalDateTime publishedAt = LocalDateTime.of(2026, 7, 16, 9, 0);
        Notice notice = Notice.builder()
                .noticeSn(7L)
                .typeCode("NTCC0003")
                .typeName("안내")
                .title("서비스 점검 안내")
                .content("서비스  점검\n시간을 안내합니다.")
                .pinnedYn("Y")
                .viewCount(12)
                .postingStartAt(publishedAt)
                .build();
        when(referenceDataService.isActiveCode("NTCG01", "NTCC0003")).thenReturn(true);
        when(noticeMapper.countPublicNotices("NTCC0003", "점검")).thenReturn(11L);
        when(noticeMapper.findPublicNotices("NTCC0003", "점검", 10L, 10)).thenReturn(List.of(notice));

        PublicNoticePageResponse result = service.getPublicNotices(" NTCC0003 ", " 점검 ", 2, 10);

        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getTotalItems()).isEqualTo(11);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).isPinned()).isTrue();
        assertThat(result.getItems().get(0).getSummary()).isEqualTo("서비스 점검 시간을 안내합니다.");
        assertThat(result.getItems().get(0).getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void returnsActiveNoticeTypesFromSharedCodeContract() {
        CommonCode type = new CommonCode();
        type.setCode("NTCC0001");
        type.setName("점검");
        when(referenceDataService.getActiveCodes("NTCG01")).thenReturn(List.of(type));

        assertThat(service.getPublicNoticeTypes())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getCode()).isEqualTo("NTCC0001");
                    assertThat(item.getName()).isEqualTo("점검");
                });
    }

    @Test
    void rejectsUnknownNoticeTypeBeforeQueryingNoticeTable() {
        when(referenceDataService.isActiveCode("NTCG01", "UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> service.getPublicNotices("UNKNOWN", null, 1, 10))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(noticeMapper, never()).countPublicNotices("UNKNOWN", null);
    }

    @Test
    void rejectsUnboundedPageSize() {
        assertThatThrownBy(() -> service.getPublicNotices(null, null, 1, 51))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void skipsOffsetQueryWhenRequestedPageIsPastLastItem() {
        when(noticeMapper.countPublicNotices(null, null)).thenReturn(3L);

        PublicNoticePageResponse result = service.getPublicNotices(null, null, 100, 10);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalItems()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(1);
        verify(noticeMapper, never()).findPublicNotices(null, null, 990L, 10);
    }

    @Test
    void rejectsKeywordLongerThanOneHundredCharactersBeforeQueryingNoticeTable() {
        assertThatThrownBy(() -> service.getPublicNotices(null, "가".repeat(101), 1, 10))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(noticeMapper, never()).countPublicNotices(null, "가".repeat(101));
    }

    @Test
    void returnsNotFoundWhenNoticeIsNotPublic() {
        when(noticeMapper.findPublicNoticeById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicNotice(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void rejectsNonPositiveNoticeIdBeforeQueryingNoticeTable() {
        assertThatThrownBy(() -> service.getPublicNotice(0L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(noticeMapper, never()).findPublicNoticeById(0L);
    }

    @Test
    void returnsPublicNoticeDetailWithoutChangingViewCount() {
        Notice notice = Notice.builder()
                .noticeSn(1L)
                .typeCode("NTCC0001")
                .typeName("점검")
                .title("점검 안내")
                .content("점검 시간 안내입니다.")
                .pinnedYn("N")
                .viewCount(3)
                .registeredAt(LocalDateTime.of(2026, 7, 16, 10, 0))
                .postingEndAt(LocalDateTime.of(2026, 7, 17, 10, 0))
                .build();
        when(noticeMapper.findPublicNoticeById(1L)).thenReturn(Optional.of(notice));

        var result = service.getPublicNotice(1L);
        assertThat(result.getViewCount()).isEqualTo(3);
        assertThat(result.getPostingEndAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 17, 10, 0));
        verify(noticeMapper).findPublicNoticeById(1L);
    }
}
