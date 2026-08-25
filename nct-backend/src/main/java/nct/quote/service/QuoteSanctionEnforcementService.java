package nct.quote.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.quote.dto.QuoteSanctionTarget;
import nct.quote.mapper.QuoteMapper;
import nct.quote.port.MemberQuoteEnforcementPort;
import nct.quote.port.QuoteEnforcementImpact;

/** 담당자 7 · 신고 처리 제재: 기간 제재는 견적 선택을 보류하고 영구정지는 안전한 견적을 철회합니다. */
@Service
@RequiredArgsConstructor
public class QuoteSanctionEnforcementService implements MemberQuoteEnforcementPort {

    private static final String SUBMITTED = "QUTC0001";
    private static final String REVISED = "QUTC0002";
    private static final String SELECTED = "QUTC0004";
    private static final String TRADE_CANCELED = "TRDC0008";

    private final QuoteMapper quoteMapper;

    @Override
    @Transactional
    public List<QuoteEnforcementImpact> pauseActiveQuotes(
            Long userSn,
            Long adminUserSn,
            String reason) {
        validate(userSn, adminUserSn, reason);

        List<QuoteEnforcementImpact> impacts = new ArrayList<>();
        for (QuoteSanctionTarget target : quoteMapper.findSanctionTargetsByMemberForUpdate(userSn)) {
            boolean providerOwned = userSn.equals(target.getProviderUserSn());
            boolean active = SUBMITTED.equals(target.getStatusCode())
                    || REVISED.equals(target.getStatusCode());
            if (!providerOwned || !active) {
                continue;
            }
            impacts.add(new QuoteEnforcementImpact(
                    target.getQuoteId(),
                    "PROVIDER",
                    "PAUSED",
                    target.getStatusCode(),
                    "7일 이용정지 동안 제공자 활성 검증으로 견적 선택을 보류했습니다."));
        }
        return List.copyOf(impacts);
    }

    @Override
    @Transactional
    public List<QuoteEnforcementImpact> withdrawActiveQuotes(
            Long userSn,
            Long adminUserSn,
            String reason) {
        validate(userSn, adminUserSn, reason);

        List<QuoteEnforcementImpact> impacts = new ArrayList<>();
        for (QuoteSanctionTarget target : quoteMapper.findSanctionTargetsByMemberForUpdate(userSn)) {
            String role = role(target, userSn);
            if (SELECTED.equals(target.getStatusCode())) {
                if (TRADE_CANCELED.equals(target.getLinkedTradeStatusCode())) {
                    int changed = quoteMapper.withdrawSelectedQuoteAfterTradeCancellation(
                            target.getQuoteId(),
                            String.valueOf(adminUserSn));
                    if (changed != 1) {
                        throw new CustomException(
                                ErrorCode.CONFLICT,
                                "연결 거래가 변경되어 선택 견적을 철회할 수 없습니다.");
                    }
                    impacts.add(new QuoteEnforcementImpact(
                            target.getQuoteId(),
                            role,
                            "CANCELED",
                            target.getStatusCode(),
                            "연결 거래 취소를 확인하고 선택 견적을 철회했습니다."));
                } else {
                    impacts.add(new QuoteEnforcementImpact(
                            target.getQuoteId(),
                            role,
                            "HELD_FOR_REVIEW",
                            target.getStatusCode(),
                            "연결 거래가 취소되지 않아 선택 견적을 자동 철회하지 않았습니다."));
                }
                continue;
            }
            int changed = quoteMapper.withdrawQuoteForSanction(
                    target.getQuoteId(),
                    target.getStatusCode(),
                    String.valueOf(adminUserSn));
            if (changed != 1) {
                throw new CustomException(ErrorCode.CONFLICT, "견적 상태가 변경되어 철회할 수 없습니다.");
            }
            impacts.add(new QuoteEnforcementImpact(
                    target.getQuoteId(),
                    role,
                    "CANCELED",
                    target.getStatusCode(),
                    "영구 이용정지로 활성 견적을 철회했습니다."));
        }
        return List.copyOf(impacts);
    }

    @Override
    @Transactional
    public boolean restorePausedQuote(
            Long quoteId,
            String previousStatusCode,
            Long adminUserSn) {
        if (quoteId == null || quoteId <= 0
                || adminUserSn == null || adminUserSn <= 0
                || (!SUBMITTED.equals(previousStatusCode) && !REVISED.equals(previousStatusCode))) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var quote = quoteMapper.findQuoteByIdForUpdate(quoteId);
        return quote != null && previousStatusCode.equals(quote.getQutStatusCd());
    }

    private void validate(Long userSn, Long adminUserSn, String reason) {
        if (userSn == null || userSn <= 0
                || adminUserSn == null || adminUserSn <= 0
                || reason == null || reason.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String role(QuoteSanctionTarget target, Long userSn) {
        boolean provider = userSn.equals(target.getProviderUserSn());
        boolean requester = userSn.equals(target.getRequestOwnerUserSn());
        if (provider && requester) return "PROVIDER_AND_REQUESTER";
        return provider ? "PROVIDER" : "REQUESTER";
    }
}
