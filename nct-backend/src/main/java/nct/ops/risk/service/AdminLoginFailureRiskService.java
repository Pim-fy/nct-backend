package nct.ops.risk.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import nct.global.security.crypto.FieldCryptoService;
import nct.ops.risk.port.AdminLoginFailureSignalStore;
import nct.ops.risk.port.RiskDetectionPolicy;
import nct.ops.risk.port.RiskDetectionPolicyReader;

/** 담당자 7 · REQ-OPS-011: 관리자 로그인 실패를 원문 노출 없이 집계합니다. */
@Service
@RequiredArgsConstructor
public class AdminLoginFailureRiskService {

    private static final int DISPLAY_TOKEN_LENGTH = 12;

    private final FieldCryptoService fieldCryptoService;
    private final AdminLoginFailureSignalStore signalStore;
    private final RiskDetectionPolicyReader policyReader;
    private final RiskEventService riskEventService;

    public void recordFailure(String loginId, String ipAddress) {
        String identityToken = token("ADMIN_LOGIN:", normalize(loginId));
        String ipToken = token("ADMIN_IP:", normalize(ipAddress));
        signalStore.record(identityToken, ipToken);

        RiskDetectionPolicy policy = policyReader.getPolicy();
        LocalDateTime since = LocalDateTime.now()
                .minusMinutes(policy.adminLoginFailWindowMinutes());
        long identityCount = signalStore.countSince(
                "identityToken", identityToken, since);
        long ipCount = signalStore.countSince(
                "ipToken", ipToken, since);

        if (identityCount >= policy.adminLoginFailCount()) {
            recordRiskEvent("계정 식별키", identityToken, policy, since);
        }
        if (ipCount >= policy.adminLoginFailCount()) {
            recordRiskEvent("접속지 식별키", ipToken, policy, since);
        }
    }

    private void recordRiskEvent(
            String sourceName,
            String token,
            RiskDetectionPolicy policy,
            LocalDateTime since) {
        riskEventService.recordOnceSince(new RiskEventCommand(
                RiskDetectionService.ADMIN_LOGIN_FAILURE,
                null,
                null,
                String.format("최근 %d분 관리자 로그인 실패 %d회 이상 · %s %s",
                        policy.adminLoginFailWindowMinutes(),
                        policy.adminLoginFailCount(),
                        sourceName,
                        token.substring(0, DISPLAY_TOKEN_LENGTH)),
                "SYSTEM"), since);
    }

    private String token(String namespace, String value) {
        return fieldCryptoService.hmac(namespace + (value == null ? "UNKNOWN" : value));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
