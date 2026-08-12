package nct.abuse.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.servlet.http.HttpServletRequest;
import nct.abuse.dto.AbuseReportFileViewRequest;
import nct.global.idempotency.SkipIdempotency;
import nct.global.security.domain.CustomUserDetails;

/** 담당자 7 · F-COM-018/F-OPS-007: 보호 파일 다운로드의 권한·캐시 경계를 검증합니다. */
class AbuseReportFileControllerTest {

    @Test
    void protectsReporterAndAdminDownloadRoutes() throws NoSuchMethodException {
        Method mine = AbuseReportFileController.class.getMethod(
                "downloadMine", Long.class, Long.class, CustomUserDetails.class);
        Method admin = AbuseReportFileController.class.getMethod(
                "downloadForAdmin",
                Long.class,
                Long.class,
                AbuseReportFileViewRequest.class,
                CustomUserDetails.class,
                HttpServletRequest.class);

        assertThat(mine.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("isAuthenticated()");
        assertThat(admin.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('ROLE_ADMIN')");
        assertThat(admin.isAnnotationPresent(SkipIdempotency.class)).isTrue();
    }
}
