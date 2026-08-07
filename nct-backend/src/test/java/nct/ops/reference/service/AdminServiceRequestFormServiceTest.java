package nct.ops.reference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.ops.reference.domain.Category;
import nct.ops.reference.mapper.CategoryMapper;
import nct.ops.reference.port.CategoryChangeHistoryCommand;
import nct.ops.reference.port.CategoryChangeHistoryPort;
import nct.servicerequest.dto.ServiceRequestFormResponse;
import nct.servicerequest.service.ServiceRequestFormManagementService;

/** 담당자 7 · F-COM-003/F-SVC-002: 폼 발행과 카테고리 노출을 같은 트랜잭션 경계로 검증한다. */
class AdminServiceRequestFormServiceTest {

    private CategoryMapper categoryMapper;
    private ServiceRequestFormManagementService managementService;
    private CategoryChangeHistoryPort historyPort;
    private AdminServiceRequestFormService service;

    @BeforeEach
    void setUp() {
        categoryMapper = org.mockito.Mockito.mock(CategoryMapper.class);
        managementService = org.mockito.Mockito.mock(ServiceRequestFormManagementService.class);
        historyPort = org.mockito.Mockito.mock(CategoryChangeHistoryPort.class);
        service = new AdminServiceRequestFormService(categoryMapper, managementService, historyPort);
    }

    @Test
    void publishingFirstFormActivatesCategoryAndRecordsAudit() {
        Category category = category();
        ServiceRequestFormResponse form = new ServiceRequestFormResponse();
        form.setFormTemplateSn(40L);
        form.setFormVersion(2);
        form.setActiveYn("Y");
        when(categoryMapper.findChildByIdAndDomainForUpdate(16L, "CATC0002"))
                .thenReturn(Optional.of(category));
        when(managementService.publish(16L, 40L, "USR:7")).thenReturn(form);
        when(categoryMapper.updateUseYn(16L, "CATC0002", "Y", "USR:7")).thenReturn(1);
        when(managementService.getActiveVersion(16L)).thenReturn(2);

        var result = service.publish(16L, 40L, 7L);

        assertThat(result.activeVersion()).isEqualTo(2);
        assertThat(result.draft()).isFalse();
        verify(categoryMapper).updateUseYn(16L, "CATC0002", "Y", "USR:7");
        ArgumentCaptor<CategoryChangeHistoryCommand> historyCaptor =
                ArgumentCaptor.forClass(CategoryChangeHistoryCommand.class);
        verify(historyPort).record(historyCaptor.capture());
        assertThat(historyCaptor.getValue().reason()).contains("폼 발행 v2");
    }

    @Test
    void discardingDraftKeepsPublishedVersionAndRecordsAudit() {
        Category category = category();
        category.setUseYn("Y");
        ServiceRequestFormResponse discarded = new ServiceRequestFormResponse();
        discarded.setFormTemplateSn(40L);
        discarded.setFormVersion(2);
        discarded.setActiveYn("N");
        ServiceRequestFormResponse active = new ServiceRequestFormResponse();
        active.setFormTemplateSn(39L);
        active.setFormVersion(1);
        active.setActiveYn("Y");
        when(categoryMapper.findChildByIdAndDomainForUpdate(16L, "CATC0002"))
                .thenReturn(Optional.of(category));
        when(managementService.discardDraft(16L, 40L, "USR:7")).thenReturn(discarded);
        when(managementService.getLatestForm(16L)).thenReturn(Optional.of(active));
        when(managementService.getActiveVersion(16L)).thenReturn(1);

        var result = service.discardDraft(16L, 40L, 7L);

        assertThat(result.form()).isSameAs(active);
        assertThat(result.activeVersion()).isEqualTo(1);
        assertThat(result.draft()).isFalse();
        ArgumentCaptor<CategoryChangeHistoryCommand> historyCaptor =
                ArgumentCaptor.forClass(CategoryChangeHistoryCommand.class);
        verify(historyPort).record(historyCaptor.capture());
        assertThat(historyCaptor.getValue().reason()).isEqualTo("서비스 요청 폼 초안 폐기 v2");
    }

    private Category category() {
        Category category = new Category();
        category.setCategorySn(16L);
        category.setParentSn(10L);
        category.setDomainCode("CATC0002");
        category.setApprovalMethodCode("CATC0004");
        category.setName("새 서비스");
        category.setProfessionalYn("Y");
        category.setSortNo(BigDecimal.valueOf(16));
        category.setUseYn("N");
        return category;
    }
}
