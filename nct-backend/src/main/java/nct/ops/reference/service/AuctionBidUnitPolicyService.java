package nct.ops.reference.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.domain.CommonCode;

/** 담당자 7 · F-AUC-013: 활성 AUCG02 목록을 새 경매의 입찰 단위 기준으로 제공합니다. */
@Service
@RequiredArgsConstructor
public class AuctionBidUnitPolicyService {

    private static final String BID_UNIT_GROUP = "AUCG02";

    private final ReferenceDataService referenceDataService;

    @Transactional(readOnly = true)
    public BigDecimal resolveActiveAmount(BigDecimal requestedAmount) {
        List<BigDecimal> activeAmounts = referenceDataService.getActiveCodes(BID_UNIT_GROUP).stream()
                .map(this::amount)
                .toList();
        if (activeAmounts.isEmpty()) {
            throw invalidConfiguration();
        }
        if (requestedAmount == null) {
            return activeAmounts.stream().min(BigDecimal::compareTo).orElseThrow(this::invalidConfiguration);
        }

        BigDecimal normalized = normalize(requestedAmount);
        return activeAmounts.stream()
                .filter(amount -> amount.compareTo(normalized) == 0)
                .findFirst()
                .orElseThrow(() -> new CustomException(
                        ErrorCode.INVALID_INPUT_VALUE,
                        "현재 사용할 수 없는 입찰 단위입니다."));
    }

    private BigDecimal amount(CommonCode code) {
        try {
            return normalize(new BigDecimal(code.getName().trim()));
        } catch (RuntimeException exception) {
            throw invalidConfiguration();
        }
    }

    private BigDecimal normalize(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.stripTrailingZeros().scale() > 0
                || amount.precision() > 15) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "입찰 단위는 1P 이상의 정수여야 합니다.");
        }
        return amount.stripTrailingZeros();
    }

    private CustomException invalidConfiguration() {
        return new CustomException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "입찰 단위 기준값을 확인할 수 없습니다.");
    }
}
