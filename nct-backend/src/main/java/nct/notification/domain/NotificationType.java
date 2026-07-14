package nct.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [알림 - 알림유형 코드]
 * - 알림이 어떤 사건인지 분류하는 공통코드(NTFG01). 화면에서 제목 앞 [배지]로 표시된다
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    AUCTION("NTFC0001"),
    BID("NTFC0002"),
    TRADE("NTFC0003"),
    SERVICE("NTFC0004"),
    OPS("NTFC0005");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
