package nct.quote.port;

import java.util.List;

/** 담당자 7 소비 계약: 거래 생성 전 활성 견적만 관리자 운영 사유로 무효화합니다. */
public interface AdminQuoteModerationPort {

    AdminQuoteModerationResult invalidateActiveQuote(
            Long serviceRequestId,
            Long quoteId,
            Long actorUserId);

    AdminQuoteBulkModerationResult invalidateActiveQuotes(
            Long serviceRequestId,
            Long actorUserId);

    record AdminQuoteModerationResult(
            Long serviceRequestId,
            Long quoteId,
            Long providerUserId,
            String previousStatusCode,
            String statusCode,
            boolean changed) {
    }

    record AdminQuoteBulkModerationResult(
            Long serviceRequestId,
            List<Long> providerUserIds,
            int changedCount) {
    }
}
