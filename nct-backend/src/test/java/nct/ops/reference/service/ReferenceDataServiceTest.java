package nct.ops.reference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.domain.Category;
import nct.ops.reference.domain.CommonCode;
import nct.ops.reference.mapper.CategoryMapper;
import nct.ops.reference.mapper.CommonCodeMapper;

class ReferenceDataServiceTest {

    private CommonCodeMapper commonCodeMapper;
    private CategoryMapper categoryMapper;
    private ReferenceDataService service;

    @BeforeEach
    void setUp() {
        commonCodeMapper = mock(CommonCodeMapper.class);
        categoryMapper = mock(CategoryMapper.class);
        service = new ReferenceDataService(commonCodeMapper, categoryMapper);
    }

    @Test
    void returnsOnlyCodeResolvedInsideRequestedGroup() {
        CommonCode code = new CommonCode();
        code.setCode("RSKC0001");
        when(commonCodeMapper.findActiveByGroupAndCode("RSKG01", "RSKC0001"))
                .thenReturn(Optional.of(code));

        CommonCode result = service.requireActiveCode("RSKG01", "RSKC0001");

        assertThat(result).isSameAs(code);
        verify(commonCodeMapper).findActiveByGroupAndCode("RSKG01", "RSKC0001");
    }

    @Test
    void rejectsUnknownOrInactiveCode() {
        when(commonCodeMapper.findActiveByGroupAndCode("RSKG01", "UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveCode("RSKG01", "UNKNOWN"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void returnsImmutableCategoryList() {
        Category category = new Category();
        category.setCategorySn(1L);
        when(commonCodeMapper.findActiveByGroupAndCode("CATG01", "CATC0001"))
                .thenReturn(Optional.of(new CommonCode()));
        when(categoryMapper.findActiveByDomain("CATC0001")).thenReturn(List.of(category));

        List<Category> result = service.getActiveCategories("CATC0001");

        assertThat(result).containsExactly(category);
        assertThatThrownBy(() -> result.add(new Category()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsCategoryOutsideExpectedDomain() {
        when(commonCodeMapper.findActiveByGroupAndCode("CATG01", "CATC0001"))
                .thenReturn(Optional.of(new CommonCode()));
        when(categoryMapper.findActiveByIdAndDomain(1L, "CATC0001"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveCategory(1L, "CATC0001"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
