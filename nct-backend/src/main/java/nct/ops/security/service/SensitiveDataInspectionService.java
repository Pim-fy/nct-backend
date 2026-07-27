package nct.ops.security.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.risk.service.RiskEventCommand;
import nct.ops.risk.service.RiskEventResult;
import nct.ops.risk.service.RiskEventService;
import nct.ops.security.port.SensitiveContentInspectionUseCase;
import nct.ops.security.port.SensitiveDetectionReportCommand;
import nct.ops.security.port.SensitiveDetectionReportPort;
import nct.ops.security.port.SensitiveDetectionReportResult;

/**
 * F-OPS-012 마스킹과 F-OPS-013 위험 이벤트 생성을 연결하는 공용 창구다.
 *
 * <p>향후 채팅 담당자와 문의 담당자는 사용자가 작성한 원문을 DB에 바로 저장하지 않고
 * 이 Service의 {@link #inspect(String, String, String, Long, String)}를 호출해야 한다.
 * 반환된 maskedText를 저장하면 개인정보 원문 저장을 피할 수 있다.</p>
 *
 * <p>개인정보가 발견되면 RISK_EVENT도 함께 만든다. detectionKey에는 이메일 같은
 * 개인정보가 아니라 요청마다 생성한 UUID를 전달해야 하며, 재시도할 때는 같은 UUID를
 * 사용해야 중복 이벤트가 만들어지지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class SensitiveDataInspectionService implements SensitiveContentInspectionUseCase {

    private static final String SENSITIVE_DATA_RISK_CODE = "RSKC0001";

    private final SensitiveDataMasker sensitiveDataMasker;
    private final RiskEventService riskEventService;
    private final SensitiveDetectionReportPort sensitiveDetectionReportPort;

    /**
     * 원문을 검사·마스킹하고, 탐지한 경우 위험 이벤트까지 기록한다.
     *
     * @param text 채팅·문의 등 사용자가 입력한 원문
     * @param detectionKey 요청별 UUID. 같은 요청 재시도에는 같은 값을 사용
     * @param referenceTypeCode 관련 거래·상품 등의 종류 코드(REFG01 소속)
     * @param referenceSn 관련 데이터 고유번호
     * @param actorId 요청 사용자 ID. 시스템 자동 처리라면 SYSTEM
     * @return 반드시 저장에 사용해야 하는 마스킹 문장과 탐지·이벤트 결과
     */
    @Override
    public SensitiveDataInspectionResult inspect(String text, String detectionKey,
                                                  String referenceTypeCode, Long referenceSn,
                                                  String actorId) {
        if (!isUuid(detectionKey)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        MaskingResult masking = sensitiveDataMasker.mask(text);
        if (masking.detectedTypes().isEmpty()) {
            return new SensitiveDataInspectionResult(
                    masking.maskedText(), masking.detectedTypes(), null, null);
        }

        // UUID 원문도 운영 화면에 그대로 남기지 않고 해시로 바꿔 이벤트 식별에만 쓴다.
        String safeSummary = "Sensitive data detected; request=" + sha256(detectionKey.trim());
        RiskEventResult riskEvent = riskEventService.recordOnce(new RiskEventCommand(
                SENSITIVE_DATA_RISK_CODE,
                referenceTypeCode,
                referenceSn,
                safeSummary,
                actorId));

        // RISK_EVENT 기록 뒤 신고 서비스가 일시 실패할 수 있으므로, 기존 이벤트를
        // 재사용한 재시도에서도 신고 포트를 호출한다. 담당자 5 구현은 riskEventSn을
        // 멱등성 키로 사용해 이미 만든 신고가 있으면 REUSED를 반환해야 한다.
        SensitiveDetectionReportResult report = sensitiveDetectionReportPort.requestReport(
                new SensitiveDetectionReportCommand(
                        riskEvent.riskEventSn(), referenceTypeCode, referenceSn,
                        masking.detectedTypes(), actorId));
        return new SensitiveDataInspectionResult(
                masking.maskedText(), masking.detectedTypes(), riskEvent, report);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /** 개인정보나 긴 임의문자열이 멱등성 키로 들어오는 것을 막기 위해 UUID만 허용한다. */
    private boolean isUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException invalidUuid) {
            return false;
        }
    }
}
