package nct.servicerequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.servicerequest.dto.AdminServiceRequestFormDraftRequest;
import nct.servicerequest.dto.AdminServiceRequestFormDraftRequest.FieldRequest;
import nct.servicerequest.dto.AdminServiceRequestFormDraftRequest.StepRequest;
import nct.servicerequest.dto.ServiceRequestFormField;
import nct.servicerequest.dto.ServiceRequestFormResponse;
import nct.servicerequest.dto.ServiceRequestFormStep;
import nct.servicerequest.mapper.ServiceRequestFormMapper;

/** 담당자 7 · F-SVC-002: 활성 버전 보존, 초안 검증, 발행 교체를 검증한다. */
class ServiceRequestFormManagementServiceTest {

    private ServiceRequestFormMapper mapper;
    private ServiceRequestFormService readService;
    private ServiceRequestFormManagementService service;

    @BeforeEach
    void setUp() {
        mapper = org.mockito.Mockito.mock(ServiceRequestFormMapper.class);
        readService = org.mockito.Mockito.mock(ServiceRequestFormService.class);
        service = new ServiceRequestFormManagementService(mapper, readService, new ObjectMapper());
    }

    @Test
    void savesNewDraftVersionWithoutUpdatingActiveTemplate() {
        when(mapper.findActiveVersion(12L)).thenReturn(1);
        when(mapper.findMaxVersion(12L)).thenReturn(2);
        when(mapper.insertTemplate(any(ServiceRequestFormResponse.class), anyString()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(0, ServiceRequestFormResponse.class).setFormTemplateSn(33L);
                    return 1;
                });
        when(mapper.insertStep(anyLong(), any(ServiceRequestFormStep.class), anyString()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, ServiceRequestFormStep.class).setStepSn(44L);
                    return 1;
                });
        when(mapper.updateTemplateFirstStep(anyLong(), anyLong(), anyString())).thenReturn(1);
        when(mapper.insertField(anyLong(), any(ServiceRequestFormField.class), anyString()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, ServiceRequestFormField.class).setFieldSn(55L);
                    return 1;
                });
        ServiceRequestFormResponse stored = new ServiceRequestFormResponse();
        stored.setFormTemplateSn(33L);
        when(readService.getFormByTemplateSn(33L)).thenReturn(stored);

        ServiceRequestFormResponse result = service.saveDraft(12L, oneInputStep(), "USR:7");

        assertThat(result.getFormTemplateSn()).isEqualTo(33L);
        verify(mapper).disableUnpublishedDrafts(12L, 1, "USR:7");
        ArgumentCaptor<ServiceRequestFormResponse> formCaptor =
                ArgumentCaptor.forClass(ServiceRequestFormResponse.class);
        verify(mapper).insertTemplate(formCaptor.capture(), org.mockito.ArgumentMatchers.eq("USR:7"));
        assertThat(formCaptor.getValue().getFormVersion()).isEqualTo(3);
        assertThat(formCaptor.getValue().getActiveYn()).isEqualTo("N");
        ArgumentCaptor<ServiceRequestFormStep> stepCaptor =
                ArgumentCaptor.forClass(ServiceRequestFormStep.class);
        verify(mapper).insertStep(anyLong(), stepCaptor.capture(), anyString());
        assertThat(stepCaptor.getValue().getSensitiveYn()).isEqualTo("N");
        assertThat(stepCaptor.getValue().getPublicYn()).isEqualTo("Y");
        ArgumentCaptor<ServiceRequestFormField> fieldCaptor =
                ArgumentCaptor.forClass(ServiceRequestFormField.class);
        verify(mapper).insertField(anyLong(), fieldCaptor.capture(), anyString());
        assertThat(fieldCaptor.getValue().getSensitiveYn()).isEqualTo("N");
        assertThat(fieldCaptor.getValue().getPublicYn()).isEqualTo("Y");
        verify(mapper, never()).deactivateActiveTemplate(anyLong(), anyString());
    }

    @Test
    void keepsExistingSensitiveFieldProtectedAndForcesAddressPrivacy() {
        ServiceRequestFormField previousSensitive = new ServiceRequestFormField();
        previousSensitive.setFieldKey("detail_address");
        previousSensitive.setSensitiveYn("Y");
        ServiceRequestFormStep previousStep = new ServiceRequestFormStep();
        previousStep.setStepKey("step_1");
        previousStep.setFields(List.of(previousSensitive));
        ServiceRequestFormResponse previousForm = new ServiceRequestFormResponse();
        previousForm.setFormTemplateSn(9L);
        previousForm.setSteps(List.of(previousStep));
        ServiceRequestFormResponse previousHeader = new ServiceRequestFormResponse();
        previousHeader.setFormTemplateSn(9L);

        when(mapper.findLatestFormHeaderByCategory(12L)).thenReturn(Optional.of(previousHeader));
        when(mapper.findActiveVersion(12L)).thenReturn(1);
        when(mapper.findMaxVersion(12L)).thenReturn(1);
        when(mapper.insertTemplate(any(ServiceRequestFormResponse.class), anyString()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(0, ServiceRequestFormResponse.class).setFormTemplateSn(33L);
                    return 1;
                });
        when(mapper.insertStep(anyLong(), any(ServiceRequestFormStep.class), anyString()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, ServiceRequestFormStep.class).setStepSn(44L);
                    return 1;
                });
        when(mapper.updateTemplateFirstStep(anyLong(), anyLong(), anyString())).thenReturn(1);
        when(mapper.insertField(anyLong(), any(ServiceRequestFormField.class), anyString()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, ServiceRequestFormField.class).setFieldSn(55L);
                    return 1;
                });
        ServiceRequestFormResponse stored = new ServiceRequestFormResponse();
        stored.setFormTemplateSn(33L);
        when(readService.getFormByTemplateSn(anyLong())).thenAnswer(invocation ->
                invocation.getArgument(0, Long.class) == 9L ? previousForm : stored);

        FieldRequest attemptedPublicDetail = new FieldRequest(
                "detail_address", "상세주소", "TEXT", null, null,
                true, false, false, true, null, null, List.of(), List.of());
        FieldRequest attemptedPublicAddress = new FieldRequest(
                "address", "주소", "ADDRESS", null, null,
                true, false, false, true, null, null, List.of(), List.of());
        AdminServiceRequestFormDraftRequest request = new AdminServiceRequestFormDraftRequest(
                null,
                null,
                List.of(new StepRequest(
                        "step_1", "주소 입력", null, "FORM", null,
                        true, false, List.of(),
                        List.of(attemptedPublicDetail, attemptedPublicAddress))));

        service.saveDraft(12L, request, "USR:7");

        ArgumentCaptor<ServiceRequestFormField> fieldCaptor =
                ArgumentCaptor.forClass(ServiceRequestFormField.class);
        verify(mapper, org.mockito.Mockito.times(2))
                .insertField(anyLong(), fieldCaptor.capture(), anyString());
        assertThat(fieldCaptor.getAllValues())
                .allSatisfy(field -> {
                    assertThat(field.getSensitiveYn()).isEqualTo("Y");
                    assertThat(field.getPublicYn()).isEqualTo("N");
                });
    }

    @Test
    void rejectsCyclicStepGraphBeforeWriting() {
        FieldRequest fieldA = field("field_a");
        FieldRequest fieldB = field("field_b");
        AdminServiceRequestFormDraftRequest request = new AdminServiceRequestFormDraftRequest(
                null,
                null,
                List.of(
                        new StepRequest("step_a", "첫 질문", null, "FORM", "step_b",
                                false, false, List.of(), List.of(fieldA)),
                        new StepRequest("step_b", "둘째 질문", null, "FORM", "step_a",
                                false, false, List.of(), List.of(fieldB))));

        assertThatThrownBy(() -> service.saveDraft(12L, request, "USR:7"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(mapper, never()).insertTemplate(any(ServiceRequestFormResponse.class), anyString());
    }

    @Test
    void publishesDraftByDeactivatingOnlyCurrentActiveVersion() {
        ServiceRequestFormResponse draft = new ServiceRequestFormResponse();
        draft.setFormTemplateSn(33L);
        draft.setCatSn(12L);
        draft.setActiveYn("N");
        draft.setUseYn("Y");
        ServiceRequestFormResponse published = new ServiceRequestFormResponse();
        published.setFormTemplateSn(33L);
        published.setActiveYn("Y");
        when(mapper.findFormHeaderForUpdate(12L, 33L)).thenReturn(Optional.of(draft));
        when(mapper.activateTemplate(12L, 33L, "USR:7")).thenReturn(1);
        when(readService.getFormByTemplateSn(33L)).thenReturn(published);

        ServiceRequestFormResponse result = service.publish(12L, 33L, "USR:7");

        assertThat(result.getActiveYn()).isEqualTo("Y");
        verify(mapper).deactivateActiveTemplate(12L, "USR:7");
        verify(mapper).activateTemplate(12L, 33L, "USR:7");
    }

    private AdminServiceRequestFormDraftRequest oneInputStep() {
        return new AdminServiceRequestFormDraftRequest(
                "안내",
                "{\"color\":\"#0064ff\"}",
                List.of(new StepRequest(
                        "step_1",
                        "요청 내용",
                        null,
                        "FORM",
                        null,
                        false,
                        false,
                        List.of(),
                        List.of(field("field_1")))));
    }

    private FieldRequest field(String key) {
        return new FieldRequest(
                key,
                "상세 내용",
                "TEXT",
                null,
                null,
                true,
                false,
                false,
                false,
                null,
                null,
                List.of(),
                List.of());
    }
}
