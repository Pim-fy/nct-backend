package nct.ops.reference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.domain.Category;
import nct.ops.reference.dto.AdminCategoryOrderRequest;
import nct.ops.reference.dto.AdminCategoryReorderRequest;
import nct.ops.reference.dto.AdminCategoryRequest;
import nct.ops.reference.mapper.CategoryMapper;
import nct.ops.reference.port.CategoryChangeHistoryCommand;
import nct.ops.reference.port.CategoryChangeHistoryPort;
import nct.servicerequest.service.ServiceRequestFormManagementService;

/** 담당자 7 · F-COM-003: 중복, 도메인, 멱등 변경 규칙을 검증한다. */
class AdminCategoryServiceTest {

    private CategoryMapper mapper;
    private ReferenceDataService referenceDataService;
    private CategoryChangeHistoryPort changeHistoryPort;
    private ServiceRequestFormManagementService formManagementService;
    private AdminCategoryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(CategoryMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        changeHistoryPort = mock(CategoryChangeHistoryPort.class);
        formManagementService = mock(ServiceRequestFormManagementService.class);
        service = new AdminCategoryService(
                mapper, referenceDataService, changeHistoryPort, formManagementService);
    }

    @Test
    void createsServiceCategoryAtEndIgnoringClientSortNumber() {
        when(mapper.findRootByDomainForUpdate("CATC0002")).thenReturn(Optional.of(category(10L, null, "서비스 거래", 10, "N", "Y")));
        when(mapper.findMaxChildSortNoByDomain("CATC0002")).thenReturn(BigDecimal.valueOf(15));
        when(mapper.insert(any(Category.class), any(String.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Category.class).setCategorySn(15L);
            return 1;
        });

        var result = service.createCategory("CATC0002", request("인테리어", 100, false, true), 7L);

        assertThat(result.categorySn()).isEqualTo(15L);
        assertThat(result.name()).isEqualTo("인테리어");
        assertThat(result.sortNo()).isEqualTo(16);
        assertThat(result.professional()).isTrue();
        assertThat(result.active()).isFalse();
        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(mapper).insert(categoryCaptor.capture(), org.mockito.ArgumentMatchers.eq("USR:7"));
        assertThat(categoryCaptor.getValue().getSortNo()).isEqualByComparingTo("16");
        assertThat(categoryCaptor.getValue().getUseYn()).isEqualTo("N");
        ArgumentCaptor<CategoryChangeHistoryCommand> auditCaptor =
                ArgumentCaptor.forClass(CategoryChangeHistoryCommand.class);
        verify(changeHistoryPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().reason()).isEqualTo("카테고리 등록");
    }

    @Test
    void keepsAutomaticActionNameWhenLegacyClientSendsMemo() {
        when(mapper.findRootByDomainForUpdate("CATC0002"))
                .thenReturn(Optional.of(category(10L, null, "서비스 거래", 10, "N", "Y")));
        when(mapper.findMaxChildSortNoByDomain("CATC0002")).thenReturn(BigDecimal.valueOf(15));
        when(mapper.insert(any(Category.class), any(String.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Category.class).setCategorySn(15L);
            return 1;
        });
        AdminCategoryRequest request =
                new AdminCategoryRequest("인테리어", 50, true, true, "분류 개편 메모");

        service.createCategory("CATC0002", request, 7L);

        ArgumentCaptor<CategoryChangeHistoryCommand> auditCaptor =
                ArgumentCaptor.forClass(CategoryChangeHistoryCommand.class);
        verify(changeHistoryPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().reason())
                .isEqualTo("카테고리 등록: 분류 개편 메모");
    }

    @Test
    void createsProductCategoryAtEndIgnoringClientSortNumber() {
        when(mapper.findRootByDomainForUpdate("CATC0001"))
                .thenReturn(Optional.of(category(1L, null, "상품", 1, "N", "Y")));
        when(mapper.findMaxChildSortNoByDomain("CATC0001")).thenReturn(BigDecimal.TEN);
        when(mapper.insert(any(Category.class), any(String.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Category.class).setCategorySn(16L);
            return 1;
        });

        var result = service.createCategory(
                "CATC0001", request("새 상품 분류", 100, true, true), 7L);

        assertThat(result.categorySn()).isEqualTo(16L);
        assertThat(result.sortNo()).isEqualTo(11);
        assertThat(result.professional()).isFalse();
        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(mapper).insert(categoryCaptor.capture(), org.mockito.ArgumentMatchers.eq("USR:7"));
        assertThat(categoryCaptor.getValue().getSortNo()).isEqualByComparingTo("11");
        assertThat(categoryCaptor.getValue().getProfessionalYn()).isEqualTo("N");
    }

    @Test
    void rejectsDuplicateNameBeforeInsert() {
        when(mapper.findRootByDomainForUpdate("CATC0002"))
                .thenReturn(Optional.of(category(10L, null, "서비스 거래", 10, "N", "Y")));
        when(mapper.countByName("CATC0002", "청소", null)).thenReturn(1);

        assertThatThrownBy(() -> service.createCategory(
                "CATC0002", request("청소", 12, true, true), 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(mapper, never()).insert(any(Category.class), any(String.class));
    }

    @Test
    void repeatedSameUpdateDoesNotWriteAgain() {
        Category stored = category(12L, 10L, "이사", 10, "Y", "Y");
        when(mapper.findRootByDomainForUpdate("CATC0002"))
                .thenReturn(Optional.of(category(10L, null, "서비스 거래", 10, "N", "Y")));
        when(mapper.findChildByIdAndDomainForUpdate(12L, "CATC0002"))
                .thenReturn(Optional.of(stored));

        var result = service.updateCategory(
                "CATC0002", 12L, request("이사", 999, true, true), 7L);

        assertThat(result.name()).isEqualTo("이사");
        verify(mapper, never()).update(any(Category.class), any(String.class));
        verify(changeHistoryPort, never()).record(any());
    }

    @Test
    void updatePreservesExistingSortNumber() {
        Category stored = category(12L, 10L, "이사", 11, "Y", "Y");
        when(mapper.findRootByDomainForUpdate("CATC0002"))
                .thenReturn(Optional.of(category(10L, null, "서비스 거래", 10, "N", "Y")));
        when(mapper.findChildByIdAndDomainForUpdate(12L, "CATC0002"))
                .thenReturn(Optional.of(stored));
        when(mapper.update(any(Category.class), any(String.class))).thenReturn(1);

        service.updateCategory("CATC0002", 12L,
                request("이사 서비스", 1, false, true), 7L);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(mapper).update(categoryCaptor.capture(), org.mockito.ArgumentMatchers.eq("USR:7"));
        assertThat(categoryCaptor.getValue().getSortNo()).isEqualByComparingTo("11");
        assertThat(categoryCaptor.getValue().getProfessionalYn()).isEqualTo("Y");
    }

    @Test
    void rejectsServiceCategoryActivationBeforeFormPublication() {
        Category stored = category(16L, 10L, "새 서비스", 16, "Y", "N");
        when(mapper.findRootByDomainForUpdate("CATC0002"))
                .thenReturn(Optional.of(category(10L, null, "서비스 거래", 10, "N", "Y")));
        when(mapper.findChildByIdAndDomainForUpdate(16L, "CATC0002"))
                .thenReturn(Optional.of(stored));
        when(formManagementService.hasActiveForm(16L)).thenReturn(false);

        assertThatThrownBy(() -> service.updateCategory(
                "CATC0002", 16L, request("새 서비스", 16, true, true), 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(mapper, never()).update(any(Category.class), any(String.class));
    }

    @Test
    void movingCategoryNormalizesDuplicateSortNumbersAndSwapsRows() {
        Category first = category(31L, 10L, "기타", 6, "Y", "Y");
        Category second = category(32L, 10L, "기타2", 10, "N", "Y");
        Category third = category(12L, 10L, "이사", 11, "Y", "Y");
        when(mapper.findRootByDomainForUpdate("CATC0002"))
                .thenReturn(Optional.of(category(10L, null, "서비스 거래", 10, "N", "Y")));
        when(mapper.findAllChildrenByDomainForUpdate("CATC0002"))
                .thenReturn(List.of(first, second, third));
        when(mapper.updateSortNo(any(Long.class), any(String.class),
                any(BigDecimal.class), any(String.class))).thenReturn(1);

        var result = service.moveCategory("CATC0002", 31L,
                new AdminCategoryOrderRequest("DOWN"), 7L);

        assertThat(result).extracting("categorySn").containsExactly(32L, 31L, 12L);
        assertThat(result).extracting("sortNo").containsExactly(11, 12, 13);
        verify(mapper, times(3)).updateSortNo(any(Long.class),
                org.mockito.ArgumentMatchers.eq("CATC0002"),
                any(BigDecimal.class), org.mockito.ArgumentMatchers.eq("USR:7"));
        verify(changeHistoryPort, times(3)).record(any());
    }

    @Test
    void reordersExactDomainCategoryListInOneTransaction() {
        Category first = category(12L, 10L, "이사", 11, "Y", "Y");
        Category second = category(11L, 10L, "청소", 12, "Y", "Y");
        Category third = category(14L, 10L, "레슨", 13, "Y", "Y");
        when(mapper.findRootByDomainForUpdate("CATC0002"))
                .thenReturn(Optional.of(category(10L, null, "서비스 거래", 10, "N", "Y")));
        when(mapper.findAllChildrenByDomainForUpdate("CATC0002"))
                .thenReturn(List.of(first, second, third));
        when(mapper.updateSortNo(any(Long.class), any(String.class),
                any(BigDecimal.class), any(String.class))).thenReturn(1);

        var result = service.reorderCategories("CATC0002",
                new AdminCategoryReorderRequest(List.of(14L, 12L, 11L)), 7L);

        assertThat(result).extracting("categorySn").containsExactly(14L, 12L, 11L);
        assertThat(result).extracting("sortNo").containsExactly(11, 12, 13);
        verify(mapper, times(3)).updateSortNo(any(Long.class),
                org.mockito.ArgumentMatchers.eq("CATC0002"),
                any(BigDecimal.class), org.mockito.ArgumentMatchers.eq("USR:7"));
    }

    @Test
    void rejectsPartialReorderList() {
        Category first = category(12L, 10L, "이사", 11, "Y", "Y");
        Category second = category(11L, 10L, "청소", 12, "Y", "Y");
        when(mapper.findRootByDomainForUpdate("CATC0002"))
                .thenReturn(Optional.of(category(10L, null, "서비스 거래", 10, "N", "Y")));
        when(mapper.findAllChildrenByDomainForUpdate("CATC0002"))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.reorderCategories("CATC0002",
                new AdminCategoryReorderRequest(List.of(12L)), 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(mapper, never()).updateSortNo(any(Long.class), any(String.class),
                any(BigDecimal.class), any(String.class));
    }

    private AdminCategoryRequest request(String name, int sortNo, boolean professional, boolean active) {
        return new AdminCategoryRequest(name, sortNo, professional, active, null);
    }

    private Category category(Long id, Long parentId, String name, int sortNo,
                              String professionalYn, String useYn) {
        Category category = new Category();
        category.setCategorySn(id);
        category.setParentSn(parentId);
        category.setDomainCode("CATC0002");
        category.setApprovalMethodCode("CATC0004");
        category.setName(name);
        category.setSortNo(BigDecimal.valueOf(sortNo));
        category.setProfessionalYn(professionalYn);
        category.setUseYn(useYn);
        return category;
    }
}
