package nct.abuse.dto;

public record ManualAbuseReportStatusResponse(
        Long reportSn,
        Long referenceSn,
        String statusCode) {
}
