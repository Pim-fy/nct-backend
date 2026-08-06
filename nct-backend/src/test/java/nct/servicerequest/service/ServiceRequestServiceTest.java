package nct.servicerequest.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import nct.file.service.FileStorageService;
import nct.global.exception.CustomException;
import nct.servicerequest.domain.ServiceRequest;
import nct.servicerequest.dto.AdminServiceRequestListItem;
import nct.servicerequest.dto.AdminServiceRequestSearchCondition;
import nct.servicerequest.dto.ServiceRequestRegisterRequest;
import nct.servicerequest.dto.ServiceRequestResponse;
import nct.servicerequest.mapper.ServiceRequestMapper;
import nct.servicerequest.mapper.SvcReqCommentMapper;
import nct.servicerequest.mapper.SvcReqImageMapper;
import nct.servicerequest.mapper.SvcReqItemMapper;
import nct.servicerequest.service.ServiceRequestFormService.ValidatedSubmission;

/** 담당자 7: F-SVC-002 템플릿 변경 시 FK 처리 순서와 구 계약 혼용 방지 회귀 테스트. */
@ExtendWith(MockitoExtension.class)
class ServiceRequestServiceTest {

    @Mock
    private ServiceRequestMapper serviceRequestMapper;
    @Mock
    private SvcReqItemMapper itemMapper;
    @Mock
    private SvcReqImageMapper imageMapper;
    @Mock
    private SvcReqCommentMapper commentMapper;
    @Mock
    private ServiceRequestFormService formService;
    @Mock
    private FileStorageService fileStorageService;

    private ServiceRequestService service;

    @BeforeEach
    void setUp() {
        service = new ServiceRequestService(
                serviceRequestMapper,
                itemMapper,
                imageMapper,
                commentMapper,
                formService,
                fileStorageService);
    }

    @Test
    void deletesOldStructuredChildrenBeforeChangingParentTemplate() {
        ServiceRequest existing = draft(1L, 7L, 10L);
        ServiceRequestRegisterRequest request = mock(ServiceRequestRegisterRequest.class);
        ValidatedSubmission submission = mock(ValidatedSubmission.class);
        ServiceRequestResponse response = ServiceRequestResponse.builder().svcReqSn(1L).build();

        when(serviceRequestMapper.findServiceRequestEntityByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(request.getCatSn()).thenReturn(8L);
        when(request.getFormTemplateSn()).thenReturn(20L);
        when(request.getSvcReqStatusCd()).thenReturn("SVCC0001");
        when(request.getStructuredAnswers()).thenReturn(List.of());
        when(request.getAddressList()).thenReturn(List.of());
        when(formService.validateSubmission(8L, 20L, 10L, List.of(), List.of(), false))
                .thenReturn(submission);
        when(submission.getFormTemplateSn()).thenReturn(20L);
        when(serviceRequestMapper.updateServiceRequest(any(ServiceRequest.class))).thenReturn(1);
        when(serviceRequestMapper.findServiceRequestById(1L)).thenReturn(Optional.of(response));

        service.updateServiceRequest(1L, 7L, request);

        InOrder order = inOrder(formService, serviceRequestMapper);
        order.verify(formService).deleteSubmission(1L);
        order.verify(serviceRequestMapper).updateServiceRequest(any(ServiceRequest.class));
        order.verify(formService).insertSubmission(1L, "7", submission);
    }

    @Test
    void rejectsReplacingStructuredAnswersWithLegacyTextItems() {
        ServiceRequest existing = draft(1L, 7L, 10L);
        ServiceRequestRegisterRequest request = mock(ServiceRequestRegisterRequest.class);

        when(serviceRequestMapper.findServiceRequestEntityByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(request.getCatSn()).thenReturn(7L);
        when(request.getFormTemplateSn()).thenReturn(null);
        when(request.getSvcReqStatusCd()).thenReturn("SVCC0001");
        when(request.getStructuredAnswers()).thenReturn(List.of());
        when(request.getAddressList()).thenReturn(List.of());
        when(request.getItems()).thenReturn(List.of("기존 문자열 형식"));
        when(formService.validateSubmission(7L, null, 10L, List.of(), List.of(), false))
                .thenReturn(null);

        assertThatThrownBy(() -> service.updateServiceRequest(1L, 7L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("기존 문자열 항목으로 교체할 수 없습니다");

        verify(serviceRequestMapper, never()).updateServiceRequest(any(ServiceRequest.class));
    }

    @Test
    void rejectsStateChangedWhileReplacingDraftSubmission() {
        ServiceRequest existing = draft(1L, 7L, 10L);
        ServiceRequestRegisterRequest request = mock(ServiceRequestRegisterRequest.class);
        ValidatedSubmission submission = mock(ValidatedSubmission.class);

        when(serviceRequestMapper.findServiceRequestEntityByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(request.getCatSn()).thenReturn(7L);
        when(request.getFormTemplateSn()).thenReturn(10L);
        when(request.getSvcReqStatusCd()).thenReturn("SVCC0002");
        when(request.getStructuredAnswers()).thenReturn(List.of());
        when(request.getAddressList()).thenReturn(List.of());
        when(formService.validateSubmission(7L, 10L, 10L, List.of(), List.of(), true))
                .thenReturn(submission);
        when(submission.getFormTemplateSn()).thenReturn(10L);
        when(serviceRequestMapper.updateServiceRequest(any(ServiceRequest.class))).thenReturn(0);

        assertThatThrownBy(() -> service.updateServiceRequest(1L, 7L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("상태가 이미 변경되었습니다");

        verify(formService).deleteSubmission(1L);
        verify(formService, never()).insertSubmission(1L, "7", submission);
    }

    @Test
    void hidesDraftFromNonOwnerDetailRequest() {
        ServiceRequest existing = draft(1L, 7L, 10L);
        when(serviceRequestMapper.findServiceRequestEntityById(1L)).thenReturn(Optional.of(existing));
        when(serviceRequestMapper.findPublicServiceRequestById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getServiceRequest(1L, 8L, true))
                .isInstanceOf(CustomException.class);

        verify(serviceRequestMapper, never()).findServiceRequestById(1L);
        verify(itemMapper, never()).findAllItemContentsBySvcReqSn(1L);
    }

    @Test
    void returnsOwnerProjectionForDraftOwner() {
        ServiceRequest existing = draft(1L, 7L, 10L);
        ServiceRequestResponse response = ServiceRequestResponse.builder().svcReqSn(1L).build();
        when(serviceRequestMapper.findServiceRequestEntityById(1L)).thenReturn(Optional.of(existing));
        when(serviceRequestMapper.findServiceRequestById(1L)).thenReturn(Optional.of(response));
        when(itemMapper.findAllItemContentsBySvcReqSn(1L)).thenReturn(List.of("전체 답변"));
        when(imageMapper.findImagesBySvcReqSn(1L)).thenReturn(List.of());
        when(formService.getOwnerAnswers(1L)).thenReturn(List.of());
        when(formService.getOwnerAddresses(1L)).thenReturn(List.of());

        ServiceRequestResponse result = service.getServiceRequest(1L, 7L, false);

        assertThat(result.getItems()).containsExactly("전체 답변");
        verify(serviceRequestMapper, never()).findPublicServiceRequestById(1L);
    }

    @Test
    void rejectsRegularUserViewingAnotherMembersRequest() {
        ServiceRequest existing = ServiceRequest.builder()
                .svcReqSn(1L)
                .usrSn(7L)
                .catSn(1L)
                .formTemplateSn(10L)
                .svcReqStatusCd("SVCC0002")
                .build();
        when(serviceRequestMapper.findServiceRequestEntityById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.getServiceRequest(1L, 8L, false))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("접근 권한이 없습니다");

        verify(serviceRequestMapper, never()).findPublicServiceRequestById(1L);
        verify(itemMapper, never()).findPublicItemContentsBySvcReqSn(1L);
    }

    @Test
    void returnsOnlyPublicProjectionForProvider() {
        ServiceRequest existing = ServiceRequest.builder()
                .svcReqSn(1L)
                .usrSn(7L)
                .catSn(1L)
                .formTemplateSn(10L)
                .svcReqStatusCd("SVCC0002")
                .build();
        ServiceRequestResponse response = ServiceRequestResponse.builder().svcReqSn(1L).build();
        when(serviceRequestMapper.findServiceRequestEntityById(1L)).thenReturn(Optional.of(existing));
        when(serviceRequestMapper.findPublicServiceRequestById(1L)).thenReturn(Optional.of(response));
        when(itemMapper.findPublicItemContentsBySvcReqSn(1L)).thenReturn(List.of("공개 답변"));
        when(imageMapper.findImagesBySvcReqSn(1L)).thenReturn(List.of());

        ServiceRequestResponse result = service.getServiceRequest(1L, 8L, true);

        assertThat(result.getItems()).containsExactly("공개 답변");
        verify(itemMapper, never()).findAllItemContentsBySvcReqSn(1L);
    }

    @Test
    void returnsPublicProjectionWhenProviderViewsOwnRequest() {
        ServiceRequest existing = ServiceRequest.builder()
                .svcReqSn(1L)
                .usrSn(7L)
                .catSn(1L)
                .formTemplateSn(10L)
                .svcReqStatusCd("SVCC0002")
                .svcReqUseYn('Y')
                .build();
        ServiceRequestResponse response = ServiceRequestResponse.builder().svcReqSn(1L).build();
        when(serviceRequestMapper.findServiceRequestEntityById(1L)).thenReturn(Optional.of(existing));
        when(serviceRequestMapper.findPublicServiceRequestById(1L)).thenReturn(Optional.of(response));
        when(itemMapper.findPublicItemContentsBySvcReqSn(1L)).thenReturn(List.of("공개 답변"));
        when(imageMapper.findImagesBySvcReqSn(1L)).thenReturn(List.of());

        ServiceRequestResponse result = service.getServiceRequest(1L, 7L, true);

        assertThat(result.getItems()).containsExactly("공개 답변");
        verify(serviceRequestMapper, never()).findServiceRequestById(1L);
        verify(formService, never()).getOwnerAddresses(1L);
    }

    @Test
    void locksAndReturnsOnlyOpenRequestForQuote() {
        ServiceRequest existing = ServiceRequest.builder()
                .svcReqSn(1L)
                .usrSn(7L)
                .catSn(3L)
                .svcReqStatusCd("SVCC0002")
                .svcReqUseYn('Y')
                .build();
        when(serviceRequestMapper.findServiceRequestEntityByIdForUpdate(1L)).thenReturn(Optional.of(existing));

        var target = service.requireOpenForQuote(1L);

        assertThat(target.requesterUsrSn()).isEqualTo(7L);
        assertThat(target.categorySn()).isEqualTo(3L);
    }

    @Test
    void rejectsClosedRequestForQuote() {
        ServiceRequest existing = ServiceRequest.builder()
                .svcReqSn(1L)
                .usrSn(7L)
                .catSn(3L)
                .svcReqStatusCd("SVCC0004")
                .svcReqUseYn('Y')
                .build();
        when(serviceRequestMapper.findServiceRequestEntityByIdForUpdate(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.requireOpenForQuote(1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("존재하지 않는 서비스 요청");
    }

    @Test
    void providerCanReadLinkedImageOnlyFromPublicProjection() {
        ServiceRequest existing = ServiceRequest.builder()
                .svcReqSn(1L)
                .usrSn(7L)
                .catSn(3L)
                .svcReqStatusCd("SVCC0002")
                .svcReqUseYn('Y')
                .build();
        when(serviceRequestMapper.findServiceRequestEntityById(1L)).thenReturn(Optional.of(existing));
        when(serviceRequestMapper.findPublicServiceRequestById(1L))
                .thenReturn(Optional.of(ServiceRequestResponse.builder().svcReqSn(1L).build()));
        when(imageMapper.countImageLink(1L, 9L)).thenReturn(1);

        service.requireImageAccess(1L, 9L, 7L, true);

        verify(serviceRequestMapper).findPublicServiceRequestById(1L);
        verify(imageMapper).countImageLink(1L, 9L);
    }

    @Test
    void validatesImageOwnerAndServiceBeforeLinkingFile() {
        ServiceRequest existing = ServiceRequest.builder()
                .svcReqSn(1L)
                .usrSn(7L)
                .catSn(3L)
                .svcReqStatusCd("SVCC0001")
                .svcReqUseYn('Y')
                .build();
        ServiceRequestRegisterRequest request = mock(ServiceRequestRegisterRequest.class);
        ServiceRequestResponse response = ServiceRequestResponse.builder().svcReqSn(1L).build();
        when(serviceRequestMapper.findServiceRequestEntityByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(request.getCatSn()).thenReturn(3L);
        when(request.getFormTemplateSn()).thenReturn(null);
        when(request.getSvcReqStatusCd()).thenReturn("SVCC0001");
        when(request.getStructuredAnswers()).thenReturn(List.of());
        when(request.getAddressList()).thenReturn(List.of());
        when(request.getFlSnList()).thenReturn(List.of(9L));
        when(formService.validateSubmission(3L, null, null, List.of(), List.of(), false)).thenReturn(null);
        when(serviceRequestMapper.updateServiceRequest(any(ServiceRequest.class))).thenReturn(1);
        when(serviceRequestMapper.findServiceRequestById(1L)).thenReturn(Optional.of(response));

        service.updateServiceRequest(1L, 7L, request);

        verify(fileStorageService).requireOwnedServiceRequestFile(9L, 7L);
        verify(imageMapper).insertAll(any());
    }

    @Test
    void returnsServerPagedAdminServiceRequests() {
        AdminServiceRequestSearchCondition condition = AdminServiceRequestSearchCondition.builder()
                .page(2)
                .size(20)
                .statusCode("SVCC0002")
                .build();
        AdminServiceRequestListItem item = AdminServiceRequestListItem.builder()
                .serviceRequestId(1256L)
                .title("입주 청소")
                .build();
        when(serviceRequestMapper.countAdminServiceRequests(condition)).thenReturn(21L);
        when(serviceRequestMapper.findAdminServiceRequestPage(condition)).thenReturn(List.of(item));

        var result = service.readPage(condition);

        assertThat(result.getItems()).containsExactly(item);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getTotalItems()).isEqualTo(21L);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void rejectsMissingAdminServiceRequestDetail() {
        when(serviceRequestMapper.findAdminServiceRequestDetail(1256L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.readDetail(1256L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("존재하지 않는 서비스 요청");
    }

    private ServiceRequest draft(Long svcReqSn, Long catSn, Long formTemplateSn) {
        return ServiceRequest.builder()
                .svcReqSn(svcReqSn)
                .usrSn(7L)
                .catSn(catSn)
                .formTemplateSn(formTemplateSn)
                .svcReqStatusCd("SVCC0001")
                .build();
    }
}
