package nct.customerinquiry.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import nct.customerinquiry.dto.AdminCustomerInquiryAnswerRequest;
import nct.customerinquiry.dto.AdminCustomerInquiryStartRequest;
import nct.customerinquiry.dto.CustomerInquiryCreateRequest;
import nct.customerinquiry.service.CustomerInquiryService;
import nct.global.security.domain.CustomUserDetails;
import nct.global.security.port.AuthMember;

/** 담당자 7 · 사용자/관리자 문의 API의 인증 역할과 전달값을 검증한다. */
class CustomerInquiryControllerTest {

    private static final String REQUEST_ID = "6253b951-a8c6-4e1d-9047-2d2c4139b444";

    @Test
    void userControllerAllowsUserAndProviderRolesOnly() {
        PreAuthorize annotation = CustomerInquiryController.class.getAnnotation(PreAuthorize.class);
        assertThat(annotation.value())
                .isEqualTo("hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')");
    }

    @Test
    void adminControllerRequiresAdminRole() {
        PreAuthorize annotation = AdminCustomerInquiryController.class.getAnnotation(PreAuthorize.class);
        assertThat(annotation.value()).isEqualTo("hasAuthority('ROLE_ADMIN')");
    }

    @Test
    void userControllerForwardsAuthenticatedOwner() {
        CustomerInquiryService service = mock(CustomerInquiryService.class);
        CustomerInquiryController controller = new CustomerInquiryController(service);
        CustomerInquiryCreateRequest request = new CustomerInquiryCreateRequest(
                "INQC0001", "제목", "본문", REQUEST_ID);

        controller.create(userDetails(10L, "ROLE_SERVICE"), request);
        controller.getMyInquiries(userDetails(10L, "ROLE_SERVICE"), "INQC0007", 2, 10);
        controller.getMyInquiry(userDetails(10L, "ROLE_SERVICE"), 51L);

        verify(service).create(10L, request);
        verify(service).getMyInquiries(10L, "INQC0007", 2, 10);
        verify(service).getMyInquiry(10L, 51L);
    }

    @Test
    void adminControllerForwardsFilterAndStateCommands() {
        CustomerInquiryService service = mock(CustomerInquiryService.class);
        AdminCustomerInquiryController controller = new AdminCustomerInquiryController(service);
        CustomUserDetails admin = userDetails(7L, "ROLE_ADMIN");
        AdminCustomerInquiryStartRequest start =
                new AdminCustomerInquiryStartRequest(REQUEST_ID);
        AdminCustomerInquiryAnswerRequest answer =
                new AdminCustomerInquiryAnswerRequest("답변", REQUEST_ID);

        controller.getInquiries("INQC0007", "INQC0001", "51", 1, 20);
        controller.getInquiry(51L);
        controller.startProcessing(admin, 51L, start);
        controller.answer(admin, 51L, answer);

        verify(service).getAdminInquiries("INQC0007", "INQC0001", "51", 1, 20);
        verify(service).getAdminInquiry(51L);
        verify(service).startProcessing(51L, 7L, REQUEST_ID);
        verify(service).answer(51L, 7L, answer);
    }

    private CustomUserDetails userDetails(Long userSn, String role) {
        return new CustomUserDetails(AuthMember.builder()
                .id(userSn)
                .email("test@example.com")
                .password("{noop}test")
                .role(role)
                .status("USRC0001")
                .build());
    }
}
