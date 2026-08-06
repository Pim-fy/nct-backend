package nct.ops.servicequery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.global.exception.CustomException;
import nct.ops.reference.dto.AdminCategoryResponse;
import nct.ops.reference.service.AdminCategoryService;
import nct.ops.servicequery.dto.AdminServiceRequestListRequest;
import nct.ops.security.service.SensitiveDataMasker;
import nct.servicerequest.dto.AdminServiceRequestListItem;
import nct.servicerequest.dto.AdminServiceRequestPage;
import nct.servicerequest.dto.AdminServiceRequestSearchCondition;
import nct.servicerequest.port.AdminServiceRequestReader;

/** 담당자 7: 관리자 서비스 요청 검색값의 정규화와 계약 경계를 검증한다. */
class AdminServiceRequestQueryServiceTest {
    private AdminServiceRequestReader reader;
    private AdminCategoryService adminCategoryService;
    private AdminServiceRequestQueryService service;

    @BeforeEach
    void setUp() {
        reader = mock(AdminServiceRequestReader.class);
        adminCategoryService = mock(AdminCategoryService.class);
        service = new AdminServiceRequestQueryService(
                reader,
                new SensitiveDataMasker(),
                adminCategoryService);
    }

    @Test
    void normalizesSearchAndDelegatesToReader() {
        AdminServiceRequestListRequest request = new AdminServiceRequestListRequest();
        request.setKeyword("  청소  ");
        request.setCategorySn(11L);
        request.setStatusCode("  SVCC0002  ");
        request.setPage(0);
        request.setSize(100);
        AdminServiceRequestListItem item = AdminServiceRequestListItem.builder()
                .serviceRequestId(1L)
                .title("연락처 010-1234-5678")
                .build();
        AdminServiceRequestPage expected = AdminServiceRequestPage.builder()
                .items(List.of(item)).page(1).size(50).totalItems(1).totalPages(1).build();
        when(adminCategoryService.getCategories("CATC0002"))
                .thenReturn(List.of(new AdminCategoryResponse(
                        11L, "CATC0002", "청소", 10, true, true)));
        when(reader.readPage(any(AdminServiceRequestSearchCondition.class))).thenReturn(expected);

        AdminServiceRequestPage result = service.getPage(request);

        ArgumentCaptor<AdminServiceRequestSearchCondition> captor =
                ArgumentCaptor.forClass(AdminServiceRequestSearchCondition.class);
        verify(reader).readPage(captor.capture());
        assertThat(result).isSameAs(expected);
        assertThat(captor.getValue().getKeyword()).isEqualTo("청소");
        assertThat(captor.getValue().getCategorySn()).isEqualTo(11L);
        assertThat(captor.getValue().getStatusCode()).isEqualTo("SVCC0002");
        assertThat(captor.getValue().getPage()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(50);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("연락처 [연락처 마스킹]");
    }

    @Test
    void rejectsInvalidDateRangeBeforeReading() {
        AdminServiceRequestListRequest request = new AdminServiceRequestListRequest();
        request.setRegisteredFrom(LocalDate.of(2026, 8, 2));
        request.setRegisteredTo(LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> service.getPage(request)).isInstanceOf(CustomException.class);
        verify(reader, never()).readPage(any());
    }

    @Test
    void rejectsInvalidDetailIdBeforeReading() {
        assertThatThrownBy(() -> service.getDetail(0L)).isInstanceOf(CustomException.class);
        verify(reader, never()).readDetail(any());
    }

    @Test
    void rejectsCategoryOutsideServiceDomainBeforeReading() {
        AdminServiceRequestListRequest request = new AdminServiceRequestListRequest();
        request.setCategorySn(999L);
        when(adminCategoryService.getCategories("CATC0002")).thenReturn(List.of());

        assertThatThrownBy(() -> service.getPage(request)).isInstanceOf(CustomException.class);
        verify(reader, never()).readPage(any());
    }
}
