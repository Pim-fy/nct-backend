package nct.ops.security.service;

import java.util.Set;

/**
 * 문자열 한 건을 마스킹한 결과다.
 *
 * @param maskedText 개인정보를 표시문구로 바꾼 안전한 문자열
 * @param detectedTypes 어떤 종류의 개인정보를 찾았는지 나타내는 집합
 */
public record MaskingResult(String maskedText, Set<SensitiveDataType> detectedTypes) {

    /** 하나 이상의 개인정보 패턴이 발견됐는지 간단히 확인한다. */
    public boolean detected() {
        return !detectedTypes.isEmpty();
    }
}
