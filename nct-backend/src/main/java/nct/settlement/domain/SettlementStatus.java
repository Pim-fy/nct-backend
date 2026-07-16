package nct.settlement.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettlementStatus {

    PENDING("STLC0001"),
    ON_HOLD("STLC0002"),
    COMPLETED("STLC0003");

    private final String code;
}
