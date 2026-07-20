package nct.ops.reference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.domain.Category;
import nct.ops.reference.dto.AdminCategoryRequest;
import nct.ops.reference.mapper.CategoryMapper;
import nct.ops.reference.port.CategoryChangeHistoryPort;

/** 담당자 7 · F-COM-003: 중복, 도메인, 멱등 변경 규칙을 검증한다. */
class AdminCategoryServiceTest {

    private CategoryMapper mapper;
    private ReferenceDataService referenceDataService;
    private CategoryChangeHistoryPort changeHistoryPort;
    private AdminCategoryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(CategoryMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        changeHistoryPort = mock(CategoryChangeHistoryPort.class);
        service = new AdminCategoryService(mapper, referenceDataService, changeHistoryPort);
    }

    @Test
    void createsChildCategoryWithFixedApprovalContract() {
        when(mapper.findRootByDomainForUpdate("CATC0002")).thenReturn(Optional.of(category(10L, null, "서비스 거래", 10, "N", "Y")));
        when(mapper.insert(any(Category.class), any(String.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Category.class).setCategorySn(15L);
            return 1;
        });

        var result = service.createCategory("CATC0002", request("인테리어", 50, true, true), 7L);

        assertThat(result.categorySn()).isEqualTo(15L);
        assertThat(result.name()).isEqualTo("인테리어");
        verify(mapper).insert(any(Category.class), org.mockito.ArgumentMatchers.eq("USR:7"));
        verify(changeHistoryPort).record(any());
    }

    @Test
    void rejectsDuplicateNameBeforeInsert() {
        when(mapper.countByName("CATC0001", "전자기기", null)).thenReturn(1);

        assertThatThrownBy(() -> service.createCategory(
                "CATC0001", request("전자기기", 10, false, true), 7L))
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
                "CATC0002", 12L, request("이사", 10, true, true), 7L);

        assertThat(result.name()).isEqualTo("이사");
        verify(mapper, never()).update(any(Category.class), any(String.class));
        verify(changeHistoryPort, never()).record(any());
    }

    private AdminCategoryRequest request(String name, int sortNo, boolean professional, boolean active) {
        return new AdminCategoryRequest(name, sortNo, professional, active, "카테고리 정책 반영");
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
