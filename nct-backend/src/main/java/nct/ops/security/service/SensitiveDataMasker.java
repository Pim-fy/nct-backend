package nct.ops.security.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * F-OPS-012에서 사용하는 순수 문자열 마스킹 도구다.
 *
 * <p>채팅·문의 저장 전에는 {@code SensitiveDataInspectionService}가 이 도구를 사용한다.
 * 공통 접근 로그와 예외 로그에서는 이 도구를 직접 호출해 개인정보가 로그 파일에
 * 남지 않도록 한다. DB 조회나 저장은 하지 않으므로 문자열만 넣어 단독 사용도 가능하다.</p>
 *
 * <p>현재 탐지 대상은 이메일, 국내 전화번호, 대표번호, 10~16자리 계좌번호 후보다.
 * 긴 숫자는 주문번호도 계좌번호로 오인할 수 있으므로 향후 정책 확정 시 개선 대상이다.</p>
 */
@Component
public class SensitiveDataMasker {

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![a-z0-9._%+-])[a-z0-9._%+-]{1,64}@[a-z0-9.-]+\\.[a-z]{2,}(?![a-z0-9._%+-])");

    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:(?:\\+?82)[- .]?)?0?(?:1[016789]|2|[3-6][1-5]|70|80|50\\d)[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)");

    private static final Pattern REPRESENTATIVE_PHONE = Pattern.compile(
            "(?<!\\d)(?:15|16|18)\\d{2}[- .]?\\d{4}(?!\\d)");

    private static final Pattern ACCOUNT_WITH_SEPARATOR = Pattern.compile(
            "(?<!\\d)(?:\\d{2,6}[- ]+){1,3}\\d{2,6}(?!\\d)");

    private static final Pattern LONG_ACCOUNT_NUMBER = Pattern.compile(
            "(?<!\\d)\\d{10,16}(?!\\d)");

    /** 문자열을 검사해 개인정보 부분을 안내 문구로 교체하고 탐지 종류도 함께 반환한다. */
    public MaskingResult mask(String text) {
        if (text == null || text.isEmpty()) {
            return new MaskingResult(text, Set.of());
        }

        Set<SensitiveDataType> detected = new LinkedHashSet<>();
        String masked = replace(EMAIL, text, "[이메일 마스킹]", SensitiveDataType.EMAIL, detected);
        masked = replace(PHONE, masked, "[연락처 마스킹]", SensitiveDataType.PHONE_NUMBER, detected);
        masked = replace(REPRESENTATIVE_PHONE, masked, "[contact masked]",
                SensitiveDataType.PHONE_NUMBER, detected);
        masked = replaceSeparatedAccount(masked, detected);
        masked = replace(LONG_ACCOUNT_NUMBER, masked, "[계좌번호 마스킹]",
                SensitiveDataType.ACCOUNT_NUMBER, detected);

        return new MaskingResult(masked, Set.copyOf(detected));
    }

    /** 탐지 종류가 필요 없고, 안전하게 바뀐 문자열만 필요할 때 사용한다. */
    public String maskText(String text) {
        return mask(text).maskedText();
    }

    /**
     * URL 경로에 인코딩된 이메일 등이 숨어 있어도 먼저 디코딩한 뒤 마스킹한다.
     * 접근 로그와 401·403·예외 응답의 path 보호에 사용한다.
     */
    public String maskUri(String uri) {
        if (uri == null) {
            return null;
        }
        try {
            return maskText(URLDecoder.decode(uri, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException malformedEncoding) {
            return maskText(uri);
        }
    }

    private String replace(Pattern pattern, String source, String replacement,
                           SensitiveDataType type, Set<SensitiveDataType> detected) {
        var matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return source;
        }
        detected.add(type);
        return matcher.replaceAll(replacement);
    }

    /** 하이픈이나 공백으로 나뉜 숫자의 실제 자릿수를 세어 계좌번호 후보를 가린다. */
    private String replaceSeparatedAccount(String source, Set<SensitiveDataType> detected) {
        Matcher matcher = ACCOUNT_WITH_SEPARATOR.matcher(source);
        StringBuffer output = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            long digitCount = matcher.group().chars().filter(Character::isDigit).count();
            if (digitCount >= 10 && digitCount <= 16) {
                matcher.appendReplacement(output, Matcher.quoteReplacement("[계좌번호 마스킹]"));
                found = true;
            } else {
                matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(output);
        if (found) {
            detected.add(SensitiveDataType.ACCOUNT_NUMBER);
        }
        return output.toString();
    }
}
