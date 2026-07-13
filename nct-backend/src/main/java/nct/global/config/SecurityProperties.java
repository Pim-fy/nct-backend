package nct.global.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * [설정 - Security 화이트리스트 프로퍼티]
 * - permit-all 경로를 application.properties 에서 관리
 *   : 새 프로젝트에 이식할 때 Java 코드 수정 없이 경로 목록만 바꾸면 됨
 *
 * application.properties 예시
 *   app.security.permit-all-paths=/api/auth/login,/api/public/**
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /** 인증 없이 허용할 URL 패턴 목록 */
    private List<String> permitAllPaths = List.of();
}
