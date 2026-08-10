package nct.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdminLoginSecurityConfigContractTest {

    // 담당자 7 · F-OPS-001: Git에 포함되는 보안 설정이 관리자 로그인 POST를 명시적으로 공개해야 한다.
    @Test
    void 관리자_전용_로그인_POST는_SecurityConfig에서_공개한다() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/nct/global/config/SecurityConfig.java"));

        assertThat(source).containsPattern(
                "\\.requestMatchers\\(HttpMethod\\.POST, \\\"/api/auth/admin/login\\\"\\)\\R\\s*\\.permitAll\\(\\)");
    }
}
