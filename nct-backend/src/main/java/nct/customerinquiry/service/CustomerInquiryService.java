package nct.customerinquiry.service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLogType;
import nct.common.domain.RefType;
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
import nct.member.port.CustomerInquiryWithdrawalPort;
import nct.member.port.MemberEmailReader;
import nct.ops.audit.port.AuditLogCommand;
import nct.ops.audit.port.AuditLogPort;
import nct.ops.reference.service.ReferenceDataService;
import nct.ops.security.port.SensitiveContentInspectionUseCase;

/**
 * 담당자 7 · 로그인 사용자의 관리자 대상 1:1 문의와 관리자 처리 흐름을 담당한다.
 * 원문 저장, 상태 변경, 감사 기록은 각각 하나의 트랜잭션에서 완료하거나 함께 롤백한다.
 */
@Service
@RequiredArgsConstructor
public class CustomerInquiryService implements CustomerInquiryWithdrawalPort {

    static final String TYPE_GROUP = "INQG01";
    static final String STATUS_GROUP = "INQG02";
    static final String RECEIVED_STATUS = "INQC0007";
    static final String PROCESSING_STATUS = "INQC0008";
    static final String ANSWERED_STATUS = "INQC0009";
    // @ai_generated: F-AUTH-017/POL-AUTH-016 - 정지 계정 비로그인 문의 유형
    static final String SUSPENDED_INQUIRY_TYPE = "INQC0010";

    private static final String PLACEHOLDER = "[민감정보 검사 중]";
    private static final String REFERENCE_TYPE = "REFC0013";
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final CustomerInquiryMapper customerInquiryMapper;
    private final ReferenceDataService referenceDataService;
    private final SensitiveContentInspectionUseCase inspectionUseCase;
    private final AuditLogPort auditLogPort;
    private final AdminMemberIdentityReader memberIdentityReader;
    // @ai_generated: F-AUTH-017/POL-AUTH-016 - 정지 계정 문의(INQC0010) 답변 통보용
    private final MemberEmailReader memberEmailReader;
    private final EmailSender emailSender;

    /** 원문을 DB에 넣지 않고 문의 번호 발급 후 필드별 검사 결과만 저장한다. */
    @Transactional
    public CustomerInquiryCreateResponse create(
            Long userSn,
            CustomerInquiryCreateRequest request) {
        validateUser(userSn);
        if (request == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String inquiryTypeCode = normalizeRequired(request.inquiryTypeCode(), 30);
        String title = normalizeRequired(request.title(), 200);
        String content = normalizeRequired(request.content(), 4000);
        String detectionKey = normalizeUuid(request.detectionKey());
        referenceDataService.requireActiveCode(TYPE_GROUP, inquiryTypeCode);
        referenceDataService.requireActiveCode(STATUS_GROUP, RECEIVED_STATUS);

        String actorId = String.valueOf(userSn);
        CustomerInquiry inquiry = CustomerInquiry.builder()
                .userSn(userSn)
                .typeCode(inquiryTypeCode)
                .statusCode(RECEIVED_STATUS)
                .title(PLACEHOLDER)
                .content(PLACEHOLDER)
                .registeredBy(actorId)
                .updatedBy(actorId)
                .build();
        int inserted = customerInquiryMapper.insertPlaceholder(inquiry);
        if (inserted != 1 || inquiry.getInquirySn() == null) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }

        String maskedTitle = inspectionUseCase.inspect(
                title,
                deriveDetectionKey(detectionKey, "title"),
                REFERENCE_TYPE,
                inquiry.getInquirySn(),
                actorId).maskedText();
        String maskedContent = inspectionUseCase.inspect(
                content,
                deriveDetectionKey(detectionKey, "content"),
                REFERENCE_TYPE,
                inquiry.getInquirySn(),
                actorId).maskedText();

        int updated = customerInquiryMapper.updateMaskedContent(
                inquiry.getInquirySn(), userSn, maskedTitle, maskedContent, actorId);
        if (updated != 1) {
            throw new CustomException(ErrorCode.DATABASE_ERROR);
        }
        return new CustomerInquiryCreateResponse(inquiry.getInquirySn());
    }

    /** 작성자 번호를 SQL 조건에 포함해 다른 사용자의 문의 조회를 차단한다. */
    @Transactional(readOnly = true)
    public PageResponse<CustomerInquiryListItemResponse> getMyInquiries(
            Long userSn,
            String statusCode,
            int page,
            int size) {
        validateUser(userSn);
        validatePage(page, size);
        String normalizedStatus = normalizeOptionalStatus(statusCode);
        long offset = (long) (page - 1) * size;
        long total = customerInquiryMapper.countMyInquiries(userSn, normalizedStatus);
        List<CustomerInquiryListItemResponse> content = total == 0 || offset >= total
                ? List.of()
                : customerInquiryMapper.findMyInquiries(userSn, normalizedStatus, offset, size);
        return PageResponse.<CustomerInquiryListItemResponse>builder()
                .content(content)
                .totalCount(total)
                .page(page)
                .size(size)
                .hasNext(offset + content.size() < total)
                .build();
    }

    @Transactional(readOnly = true)
    public CustomerInquiryDetailResponse getMyInquiry(Long userSn, Long inquirySn) {
        validateUser(userSn);
        validateInquirySn(inquirySn);
        CustomerInquiryDetailResponse inquiry =
                customerInquiryMapper.findMyInquiryDetail(inquirySn, userSn);
        if (inquiry == null) {
            throw new CustomException(ErrorCode.CUSTOMER_INQUIRY_NOT_FOUND);
        }
        return inquiry;
    }

    /** 접수 상태를 우선하고 같은 상태에서는 최신 문의부터 관리자에게 반환한다. */
    @Transactional(readOnly = true)
    public AdminCustomerInquiryPageResponse getAdminInquiries(
            String statusCode,
            String inquiryTypeCode,
            String keyword,
            int page,
            int size) {
        validatePage(page, size);
        String normalizedStatus = normalizeOptionalStatus(statusCode);
        String normalizedType = normalizeOptionalType(inquiryTypeCode);
        String normalizedKeyword = normalizeKeyword(keyword);
        long offset = (long) (page - 1) * size;
        long total = customerInquiryMapper.countAdminInquiries(
                normalizedStatus, normalizedType, normalizedKeyword);
        List<AdminCustomerInquiryListItemResponse> items = total == 0 || offset >= total
                ? List.of()
                : customerInquiryMapper.findAdminInquiries(
                        normalizedStatus, normalizedType, normalizedKeyword, offset, size);
        enrichAdminInquiryList(items);
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new AdminCustomerInquiryPageResponse(items, page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public AdminCustomerInquiryDetailResponse getAdminInquiry(Long inquirySn) {
        validateInquirySn(inquirySn);
        AdminCustomerInquiryDetailResponse inquiry = requireAdminInquiry(inquirySn);
        applyAdminInquiryMembers(inquiry);
        return inquiry;
    }

    /** 접수 문의 한 건을 최초 처리 관리자에게 원자적으로 배정하고 감사기록을 남긴다. */
    @Transactional
    public void startProcessing(Long inquirySn, Long adminUserSn, String requestId) {
        validateInquirySn(inquirySn);
        validateUser(adminUserSn);
        String normalizedRequestId = normalizeUuid(requestId);
        AdminCustomerInquiryDetailResponse inquiry = requireAdminInquiry(inquirySn);
        if (!RECEIVED_STATUS.equals(inquiry.getStatusCode())
                || inquiry.getProcessorUserSn() != null
                || inquiry.getAnswer() != null) {
            throw new CustomException(ErrorCode.CUSTOMER_INQUIRY_STATUS_CONFLICT);
        }
        referenceDataService.requireActiveCode(STATUS_GROUP, PROCESSING_STATUS);

        String actorId = String.valueOf(adminUserSn);
        int updated = customerInquiryMapper.startProcessing(
                inquirySn,
                adminUserSn,
                actorId,
                RECEIVED_STATUS,
                PROCESSING_STATUS);
        if (updated != 1) {
            throw new CustomException(ErrorCode.CUSTOMER_INQUIRY_STATUS_CONFLICT);
        }
        recordStatusAudit(
                inquirySn,
                actorId,
                "문의 처리 시작",
                RECEIVED_STATUS,
                PROCESSING_STATUS,
                normalizedRequestId);
    }

    /** 담당 관리자만 처리중 문의에 마스킹된 답변을 한 번 등록할 수 있다. */
    @Transactional
    public void answer(
            Long inquirySn,
            Long adminUserSn,
            AdminCustomerInquiryAnswerRequest request) {
        validateInquirySn(inquirySn);
        validateUser(adminUserSn);
        if (request == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String answer = normalizeRequired(request.answer(), 4000);
        String detectionKey = normalizeUuid(request.detectionKey());
        AdminCustomerInquiryDetailResponse inquiry = requireAdminInquiry(inquirySn);
        if (!PROCESSING_STATUS.equals(inquiry.getStatusCode())
                || !adminUserSn.equals(inquiry.getProcessorUserSn())
                || inquiry.getAnswer() != null
                || inquiry.getAnsweredAt() != null) {
            throw new CustomException(ErrorCode.CUSTOMER_INQUIRY_STATUS_CONFLICT);
        }
        referenceDataService.requireActiveCode(STATUS_GROUP, ANSWERED_STATUS);

        String actorId = String.valueOf(adminUserSn);
        String maskedAnswer = inspectionUseCase.inspect(
                answer,
                deriveDetectionKey(detectionKey, "answer"),
                REFERENCE_TYPE,
                inquirySn,
                actorId).maskedText();
        int updated = customerInquiryMapper.completeAnswer(
                inquirySn,
                adminUserSn,
                maskedAnswer,
                actorId,
                PROCESSING_STATUS,
                ANSWERED_STATUS);
        if (updated != 1) {
            throw new CustomException(ErrorCode.CUSTOMER_INQUIRY_STATUS_CONFLICT);
        }
        recordStatusAudit(
                inquirySn,
                actorId,
                "관리자 답변 등록",
                PROCESSING_STATUS,
                ANSWERED_STATUS,
                detectionKey);

        // @ai_generated: F-AUTH-017/POL-AUTH-016 - 정지 계정이 접수한 문의(INQC0010)만 이메일로
        // 통보한다. 일반 로그인 사용자의 문의는 마이페이지에서 직접 확인하므로 이메일을 보내지 않는다
        // (기존 동작 유지 - 이번에 새로 뭔가를 막은 게 아니라 원래도 없던 알림이다).
        if (SUSPENDED_INQUIRY_TYPE.equals(inquiry.getInquiryTypeCode())) {
            String email = memberEmailReader.findEmailByUserSn(inquiry.getUserSn());
            if (email != null) {
                emailSender.sendSuspendedInquiryAnswer(email, inquiry.getTitle() + "\n" + inquiry.getContent(), answer);
            }
        }
    }

    // @ai_generated: F-AUTH-011/POL-AUTH-013 - CustomerInquiryWithdrawalPort 구현.
    // MemberService.withdraw() 트랜잭션 안에서 호출된다. 실제 답변 없이 상태만 종결로 전환하고,
    // 감사 로그만 남긴다(상대방은 항상 관리자라 별도 알림이 필요 없다 - 사용자 결정, ISS-026).
    @Override
    @Transactional
    public void closeUnansweredByUser(Long usrSn) {
        // @ai_generated: (QA P2-1) 기존 answer()와 동일하게, 전이 대상 상태가 활성 공통코드인지
        // 먼저 확인한다(startProcessing()도 같은 패턴).
        referenceDataService.requireActiveCode(STATUS_GROUP, ANSWERED_STATUS);
        int updated = customerInquiryMapper.closeUnansweredByUser(
                usrSn, RECEIVED_STATUS, PROCESSING_STATUS, ANSWERED_STATUS, String.valueOf(usrSn));
        if (updated > 0) {
            auditLogPort.record(new AuditLogCommand(
                    AuditLogType.STATUS_CHANGE.name(),
                    String.valueOf(usrSn),
                    RefType.CUSTOMER_INQUIRY.getCode(),
                    usrSn,
                    "회원 탈퇴에 따른 미답변 문의 자동 종결",
                    RECEIVED_STATUS + "/" + PROCESSING_STATUS,
                    ANSWERED_STATUS + "(" + updated + "건)",
                    null));
        }
    }

    private AdminCustomerInquiryDetailResponse requireAdminInquiry(Long inquirySn) {
        AdminCustomerInquiryDetailResponse inquiry =
                customerInquiryMapper.findAdminInquiryDetail(inquirySn);
        if (inquiry == null) {
            throw new CustomException(ErrorCode.CUSTOMER_INQUIRY_NOT_FOUND);
        }
        return inquiry;
    }

    private void enrichAdminInquiryList(List<AdminCustomerInquiryListItemResponse> inquiries) {
        if (inquiries == null || inquiries.isEmpty()) {
            return;
        }

        Set<Long> userSns = new LinkedHashSet<>();
        for (AdminCustomerInquiryListItemResponse inquiry : inquiries) {
            addUserSn(userSns, inquiry.getUserSn());
            addUserSn(userSns, inquiry.getProcessorUserSn());
        }
        Map<Long, AdminMemberIdentityResponse> identities = memberIdentityReader.findByUserSns(userSns);
        for (AdminCustomerInquiryListItemResponse inquiry : inquiries) {
            inquiry.setWriterMember(identityOf(identities, inquiry.getUserSn()));
            inquiry.setProcessorMember(identityOf(identities, inquiry.getProcessorUserSn()));
        }
    }

    private void applyAdminInquiryMembers(AdminCustomerInquiryDetailResponse inquiry) {
        Set<Long> userSns = new LinkedHashSet<>();
        addUserSn(userSns, inquiry.getUserSn());
        addUserSn(userSns, inquiry.getProcessorUserSn());
        Map<Long, AdminMemberIdentityResponse> identities = memberIdentityReader.findByUserSns(userSns);
        inquiry.setWriterMember(identityOf(identities, inquiry.getUserSn()));
        inquiry.setProcessorMember(identityOf(identities, inquiry.getProcessorUserSn()));
    }

    private AdminMemberIdentityResponse identityOf(
            Map<Long, AdminMemberIdentityResponse> identities,
            Long userSn) {
        return userSn == null ? null : identities.get(userSn);
    }

    private void addUserSn(Set<Long> userSns, Long userSn) {
        if (userSn != null && userSn > 0) {
            userSns.add(userSn);
        }
    }

    private void recordStatusAudit(
            Long inquirySn,
            String actorId,
            String reason,
            String beforeStatus,
            String afterStatus,
            String requestId) {
        auditLogPort.record(new AuditLogCommand(
                AuditLogType.STATUS_CHANGE.name(),
                actorId,
                RefType.CUSTOMER_INQUIRY.getCode(),
                inquirySn,
                reason,
                statusSummary(inquirySn, beforeStatus),
                statusSummary(inquirySn, afterStatus),
                requestId));
    }

    private String statusSummary(Long inquirySn, String statusCode) {
        return "inquirySn=" + inquirySn + ",status=" + statusCode;
    }

    private String normalizeOptionalStatus(String statusCode) {
        if (statusCode == null || statusCode.isBlank()) {
            return null;
        }
        String normalized = normalizeRequired(statusCode, 30);
        referenceDataService.requireActiveCode(STATUS_GROUP, normalized);
        return normalized;
    }

    private String normalizeOptionalType(String inquiryTypeCode) {
        if (inquiryTypeCode == null || inquiryTypeCode.isBlank()) {
            return null;
        }
        String normalized = normalizeRequired(inquiryTypeCode, 30);
        referenceDataService.requireActiveCode(TYPE_GROUP, normalized);
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return normalizeRequired(keyword, MAX_KEYWORD_LENGTH);
    }

    private String normalizeRequired(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    private String normalizeUuid(String value) {
        String normalized = normalizeRequired(value, 36);
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException invalidUuid) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String deriveDetectionKey(String requestId, String fieldName) {
        return UUID.nameUUIDFromBytes(
                (requestId + ":" + fieldName).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void validateUser(Long userSn) {
        if (userSn == null || userSn <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void validateInquirySn(Long inquirySn) {
        if (inquirySn == null || inquirySn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
