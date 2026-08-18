package nct.ops.risk.port;

/** 담당자 7 · REQ-OPS-011: 설정 소유 영역이 제공하는 읽기 전용 리스크 기준 계약입니다. */
public interface RiskDetectionPolicyReader {

    RiskDetectionPolicy getPolicy();
}
