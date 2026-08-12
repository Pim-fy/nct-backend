package nct.customerinquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import nct.ops.security.port.SensitiveContentInspectionUseCase;
import nct.ops.security.service.SensitiveDataInspectionResult;
import nct.customerinquiry.domain.CustomerInquiry;
import nct.customerinquiry.dto.AdminCustomerInquiryAnswerRequest;
import nct.customerinquiry.dto.AdminCustomerInquiryDetailResponse;
import nct.customerinquiry.dto.AdminCustomerInquiryListItemResponse;
import nct.customerinquiry.dto.AdminCustomerInquiryPageResponse;
import nct.customerinquiry.dto.CustomerInquiryCreateRequest;
import nct.customerinquiry.dto.CustomerInquiryCreateResponse;
import nct.customerinquiry.dto.CustomerInquiryDetailResponse;
import nct.customerinquiry.dto.CustomerInquiryListItemResponse;
import nct.customerinquiry.mapper.CustomerInquiryMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.response.PageResponse;
import nct.auth.service.EmailSender;
import nct.member.dto.AdminMemberIdentityResponse;
import nct.member.port.AdminMemberIdentityReader;
import nct.member.port.MemberEmailReader;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.reference.service.ReferenceDataService;

/** 담당자 7 · 고객 문의의 원문 비저장, 소유권, 상태 경쟁, 감사 계약을 검증한다. */
class CustomerInquiryServiceTest {

    private static final String DETECTION_KEY = "6253b951-a8c6-4e1d-9047-2d2c4139b444";

    private CustomerInquiryMapper mapper;
    private ReferenceDataService referenceDataService;
    private SensitiveContentInspectionUseCase inspectionUseCase;
    private AuditLogPort auditLogPort;
    private AdminMemberIdentityReader memberIdentityReader;
    // @ai_generated: F-AUTH-017/POL-AUTH-016 - 정지 계정 문의 답변 통보 협력자(기본 Mockito
    // 동작으로 충분해 개별 테스트에서 별도 stub 불필요)
    private MemberEmailReader memberEmailReader;
    private EmailSender emailSender;
    private CustomerInquiryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(CustomerInquiryMapper.class);
        referenceDataService = mock(ReferenceDataService.class);
        inspectionUseCase = mock(SensitiveContentInspectionUseCase.class);
        auditLogPort = mock(AuditLogPort.class);
        memberIdentityReader = mock(AdminMemberIdentityReader.class);
        memberEmailReader = mock(MemberEmailReader.class);
        emailSender = mock(EmailSender.class);
        when(memberIdentityReader.findByUserSns(any())).thenReturn(Map.of());
        service = new CustomerInquiryService(
                mapper,
                referenceDataService,
                inspectionUseCase,
                auditLogPort,
                memberIdentityReader,
                memberEmailReader,
                emailSender);
    }

    @Test
    void storesOnlyMaskedTitleAndContentAfterPlaceholderInsert() {
        CustomerInquiryCreateRequest request = new CustomerInquiryCreateRequest(
                " INQC0001 ",
                "010-1234-5678 계정 문의",
                "test@example.com으로 답변해 주세요",
                DETECTION_KEY);
        doAnswer(invocation -> {
            CustomerInquiry inquiry = invocation.getArgument(0);
            inquiry.setInquirySn(51L);
            return 1;
        }).when(mapper).insertPlaceholder(any(CustomerInquiry.class));
        when(inspectionUseCase.inspect(
                request.title(), derived("title"), "REFC0013", 51L, "10"))
                .thenReturn(masked("[연락처 마스킹] 계정 문의"));
        when(inspectionUseCase.inspect(
                request.content(), derived("content"), "REFC0013", 51L, "10"))
                .thenReturn(masked("[이메일 마스킹]으로 답변해 주세요"));
        when(mapper.updateMaskedContent(
                51L,
                10L,
                "[연락처 마스킹] 계정 문의",
                "[이메일 마스킹]으로 답변해 주세요",
                "10")).thenReturn(1);

        CustomerInquiryCreateResponse result = service.create(10L, request);

        assertThat(result.inquirySn()).isEqualTo(51L);
        ArgumentCaptor<CustomerInquiry> placeholderCaptor =
                ArgumentCaptor.forClass(CustomerInquiry.class);
        verify(mapper).insertPlaceholder(placeholderCaptor.capture());
        CustomerInquiry placeholder = placeholderCaptor.getValue();
        assertThat(placeholder.getTitle()).isEqualTo("[민감정보 검사 중]");
        assertThat(placeholder.getContent()).isEqualTo("[민감정보 검사 중]");
        assertThat(placeholder.getTitle()).doesNotContain("010-1234-5678");
        assertThat(placeholder.getContent()).doesNotContain("test@example.com");
        verify(referenceDataService).requireActiveCode("INQG01", "INQC0001");
        verify(referenceDataService).requireActiveCode("INQG02", "INQC0007");
        verify(mapper).updateMaskedContent(
                51L,
                10L,
                "[연락처 마스킹] 계정 문의",
                "[이메일 마스킹]으로 답변해 주세요",
                "10");
    }

    @Test
    void doesNotPersistContentWhenInspectionFails() {
        CustomerInquiryCreateRequest request = createRequest();
        doAnswer(invocation -> {
            CustomerInquiry inquiry = invocation.getArgument(0);
            inquiry.setInquirySn(51L);
            return 1;
        }).when(mapper).insertPlaceholder(any(CustomerInquiry.class));
        when(inspectionUseCase.inspect(
                request.title(), derived("title"), "REFC0013", 51L, "10"))
                .thenThrow(new CustomException(ErrorCode.DATABASE_ERROR));

        assertThatThrownBy(() -> service.create(10L, request))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DATABASE_ERROR));

        verify(mapper, never()).updateMaskedContent(
                anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void rejectsInvalidDetectionKeyBeforeInsert() {
        CustomerInquiryCreateRequest request = new CustomerInquiryCreateRequest(
                "INQC0001", "제목", "본문", "not-a-uuid");

        assertThatThrownBy(() -> service.create(10L, request))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(mapper, never()).insertPlaceholder(any());
        verify(inspectionUseCase, never()).inspect(any(), any(), any(), any(), any());
    }

    @Test
    void readsOnlyInquiryOwnedByAuthenticatedUser() {
        CustomerInquiryDetailResponse detail = new CustomerInquiryDetailResponse(
                51L, "INQC0001", "계정", "INQC0007", "접수", "제목", "본문",
                null, LocalDateTime.now(), LocalDateTime.now(), null);
        when(mapper.findMyInquiryDetail(51L, 10L)).thenReturn(detail);

        assertThat(service.getMyInquiry(10L, 51L)).isSameAs(detail);
        verify(mapper).findMyInquiryDetail(51L, 10L);

        when(mapper.findMyInquiryDetail(51L, 20L)).thenReturn(null);
        assertThatThrownBy(() -> service.getMyInquiry(20L, 51L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CUSTOMER_INQUIRY_NOT_FOUND));
    }

    @Test
    void returnsFilteredUserAndAdminPages() {
        CustomerInquiryListItemResponse myItem = new CustomerInquiryListItemResponse(
                51L, "INQC0001", "계정", "INQC0007", "접수", "제목",
                LocalDateTime.now(), LocalDateTime.now(), null);
        when(mapper.countMyInquiries(10L, "INQC0007")).thenReturn(11L);
        when(mapper.findMyInquiries(10L, "INQC0007", 10L, 10))
                .thenReturn(List.of(myItem));

        PageResponse<CustomerInquiryListItemResponse> myPage =
                service.getMyInquiries(10L, " INQC0007 ", 2, 10);

        assertThat(myPage.getContent()).containsExactly(myItem);
        assertThat(myPage.getTotalCount()).isEqualTo(11L);
        assertThat(myPage.isHasNext()).isFalse();

        AdminCustomerInquiryListItemResponse adminItem = new AdminCustomerInquiryListItemResponse(
                51L, 10L, "INQC0001", "계정", "INQC0007", "접수", "제목", null,
                LocalDateTime.now(), LocalDateTime.now(), null);
        when(mapper.countAdminInquiries("INQC0007", "INQC0001", "51"))
                .thenReturn(21L);
        when(mapper.findAdminInquiries(
                "INQC0007", "INQC0001", "51", 20L, 20))
                .thenReturn(List.of(adminItem));
        AdminMemberIdentityResponse writer = AdminMemberIdentityResponse.builder()
                .userSn(10L)
                .loginId("writer01")
                .nickname("문의작성자")
                .build();
        when(memberIdentityReader.findByUserSns(any())).thenReturn(Map.of(10L, writer));

        AdminCustomerInquiryPageResponse adminPage = service.getAdminInquiries(
                " INQC0007 ", " INQC0001 ", " 51 ", 2, 20);

        assertThat(adminPage.items()).containsExactly(adminItem);
        assertThat(adminPage.items().getFirst().getWriterMember()).isSameAs(writer);
        assertThat(adminPage.totalItems()).isEqualTo(21L);
        assertThat(adminPage.totalPages()).isEqualTo(2);
        verify(referenceDataService, times(2)).requireActiveCode("INQG02", "INQC0007");
        verify(referenceDataService).requireActiveCode("INQG01", "INQC0001");
    }

    @Test
    void startsProcessingWithConditionalUpdateAndAudit() {
        when(mapper.findAdminInquiryDetail(51L)).thenReturn(adminDetail(
                "INQC0007", null, null, null));
        when(mapper.startProcessing(
                51L, 7L, "7", "INQC0007", "INQC0008")).thenReturn(1);

        service.startProcessing(51L, 7L, DETECTION_KEY);

        ArgumentCaptor<AuditLogCommand> auditCaptor =
                ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        AuditLogCommand audit = auditCaptor.getValue();
        assertThat(audit.actionCode()).isEqualTo("STATUS_CHANGE");
        assertThat(audit.actorId()).isEqualTo("7");
        assertThat(audit.referenceTypeCode()).isEqualTo("REFC0013");
        assertThat(audit.referenceSn()).isEqualTo(51L);
        assertThat(audit.requestId()).isEqualTo(DETECTION_KEY);
    }

    @Test
    void rejectsProcessingRaceWithoutAudit() {
        when(mapper.findAdminInquiryDetail(51L)).thenReturn(adminDetail(
                "INQC0007", null, null, null));
        when(mapper.startProcessing(
                51L, 7L, "7", "INQC0007", "INQC0008")).thenReturn(0);

        assertThatThrownBy(() -> service.startProcessing(51L, 7L, DETECTION_KEY))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CUSTOMER_INQUIRY_STATUS_CONFLICT));
        verify(auditLogPort, never()).record(any());
    }

    @Test
    void storesMaskedAnswerOnlyForAssignedAdminAndAuditsCompletion() {
        when(mapper.findAdminInquiryDetail(51L)).thenReturn(adminDetail(
                "INQC0008", 7L, null, null));
        AdminCustomerInquiryAnswerRequest request = new AdminCustomerInquiryAnswerRequest(
                "010-1234-5678로 연락하지 않습니다.", DETECTION_KEY);
        when(inspectionUseCase.inspect(
                request.answer(), derived("answer"), "REFC0013", 51L, "7"))
                .thenReturn(masked("[연락처 마스킹]로 연락하지 않습니다."));
        when(mapper.completeAnswer(
                51L,
                7L,
                "[연락처 마스킹]로 연락하지 않습니다.",
                "7",
                "INQC0008",
                "INQC0009")).thenReturn(1);

        service.answer(51L, 7L, request);

        verify(mapper).completeAnswer(
                51L,
                7L,
                "[연락처 마스킹]로 연락하지 않습니다.",
                "7",
                "INQC0008",
                "INQC0009");
        ArgumentCaptor<AuditLogCommand> auditCaptor =
                ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().reason()).isEqualTo("관리자 답변 등록");
        assertThat(auditCaptor.getValue().afterSummary())
                .isEqualTo("inquirySn=51,status=INQC0009");
    }

    @Test
    void rejectsAnswerFromUnassignedAdminBeforeInspection() {
        when(mapper.findAdminInquiryDetail(51L)).thenReturn(adminDetail(
                "INQC0008", 8L, null, null));

        assertThatThrownBy(() -> service.answer(
                51L,
                7L,
                new AdminCustomerInquiryAnswerRequest("답변", DETECTION_KEY)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CUSTOMER_INQUIRY_STATUS_CONFLICT));

        verify(inspectionUseCase, never()).inspect(any(), any(), any(), any(), any());
        verify(mapper, never()).completeAnswer(anyLong(), anyLong(), any(), any(), any(), any());
        verify(auditLogPort, never()).record(any());
    }

    @Test
    void withdrawalBulkCloseReferencesMemberInsteadOfInquiryNumber() {
        when(mapper.closeUnansweredByUser(
                101L, "INQC0007", "INQC0008", "INQC0009", "101")).thenReturn(2);

        service.closeUnansweredByUser(101L);

        ArgumentCaptor<AuditLogCommand> auditCaptor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogPort).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().referenceTypeCode()).isEqualTo("REFC0001");
        assertThat(auditCaptor.getValue().referenceSn()).isEqualTo(101L);
    }

    private CustomerInquiryCreateRequest createRequest() {
        return new CustomerInquiryCreateRequest(
                "INQC0001", "계정 문의", "로그인이 되지 않습니다.", DETECTION_KEY);
    }

    private SensitiveDataInspectionResult masked(String value) {
        return new SensitiveDataInspectionResult(value, Set.of(), null, null);
    }

    private String derived(String fieldName) {
        return UUID.nameUUIDFromBytes(
                (DETECTION_KEY + ":" + fieldName).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private AdminCustomerInquiryDetailResponse adminDetail(
            String statusCode,
            Long processorUserSn,
            String answer,
            LocalDateTime answeredAt) {
        return new AdminCustomerInquiryDetailResponse(
                51L,
                10L,
                "INQC0001",
                "계정",
                statusCode,
                "상태",
                "제목",
                "본문",
                processorUserSn,
                answer,
                LocalDateTime.of(2026, 8, 8, 18, 0),
                LocalDateTime.of(2026, 8, 8, 18, 0),
                answeredAt);
    }
}
