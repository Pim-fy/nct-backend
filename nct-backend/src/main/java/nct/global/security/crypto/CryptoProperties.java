package nct.global.security.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

// @ai_generated
/**
 * 개인정보 필드 암호화 키 설정. 실제 키는 환경 변수 또는 Secret Manager에서만 주입한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

    private String aesKeyBase64;
    private String hmacKeyBase64;
}
