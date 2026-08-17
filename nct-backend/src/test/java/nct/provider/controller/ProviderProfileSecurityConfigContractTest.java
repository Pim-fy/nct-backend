package nct.provider.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ProviderProfileSecurityConfigContractTest {

    // 담당자 7 · F-PROV-016: 제공자 프로필·포트폴리오와 폐기된 목록 검색을 공개 경로로 되돌리지 않는다.
    @Test
    void 제공자_프로필과_포트폴리오는_비로그인_허용_목록에_없다() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/nct/global/config/SecurityConfig.java"));

        assertThat(source)
                .doesNotContain("/api/providers/*/profile")
                .doesNotContain("/api/providers/*/portfolios")
                .doesNotContain("/api/service-discovery/providers")
                .contains(".anyRequest()")
                .contains(".authenticated()");
    }

    @Test
    void 제공자_프로필과_포트폴리오_조회_메소드는_인증을_명시한다() throws IOException {
        String profileController = Files.readString(Path.of(
                "src/main/java/nct/provider/controller/ProviderProfileController.java"));
        String portfolioController = Files.readString(Path.of(
                "src/main/java/nct/provider/controller/PortfolioController.java"));

        assertThat(profileController).containsPattern(
                "@GetMapping\\(\"/\\{providerUserSn\\}/profile\"\\)\\s+"
                        + "@PreAuthorize\\(\"isAuthenticated\\(\\)\"\\)");
        assertThat(portfolioController).containsPattern(
                "@GetMapping\\(\"/\\{providerUserSn\\}/portfolios\"\\)\\s+"
                        + "@PreAuthorize\\(\"isAuthenticated\\(\\)\"\\)");
    }
}
