package nct.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import nct.global.response.ApiResponse;
import nct.member.dto.PublicUserProfileResponse;
import nct.member.service.PublicUserProfileService;

/** 담당자 7 통합 연결 · F-COM-008~009: 거래 프로필 경로와 허용 역할 계약을 단위 검증한다. */
@ExtendWith(MockitoExtension.class)
class PublicUserProfileControllerTest {
    private static final long PROFILE_USER_SN = 1L;

    @Mock
    private PublicUserProfileService publicUserProfileService;

    private PublicUserProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicUserProfileController(publicUserProfileService);
    }

    @Test
    void 일반과_제공자_역할만_거래_프로필을_조회할_수_있다() throws NoSuchMethodException {
        RequestMapping basePath = PublicUserProfileController.class.getAnnotation(RequestMapping.class);
        PreAuthorize authorization = PublicUserProfileController.class.getAnnotation(PreAuthorize.class);
        Method method = PublicUserProfileController.class.getMethod("getProfile", Long.class);
        GetMapping endpoint = method.getAnnotation(GetMapping.class);

        assertThat(basePath.value()).containsExactly("/api/users");
        assertThat(endpoint.value()).containsExactly("/{userSn}/profile");
        assertThat(authorization.value()).isEqualTo("hasAnyAuthority('ROLE_USER', 'ROLE_SERVICE')");
    }

    @Test
    void 응답_DTO는_공개_허용_필드_세_개만_가진다() {
        assertThat(Arrays.stream(PublicUserProfileResponse.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .containsExactlyInAnyOrder("userSn", "displayName", "profileImageUrl");
    }

    @Test
    void 조회_결과를_공통_API_응답으로_반환한다() {
        PublicUserProfileResponse profile = PublicUserProfileResponse.builder()
                .userSn(PROFILE_USER_SN)
                .displayName("거래왕")
                .profileImageUrl("/api/attachment/profile/20260811/profile.png")
                .build();
        when(publicUserProfileService.getProfile(PROFILE_USER_SN)).thenReturn(profile);

        ResponseEntity<ApiResponse<PublicUserProfileResponse>> response = controller.getProfile(PROFILE_USER_SN);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(profile);
    }
}
