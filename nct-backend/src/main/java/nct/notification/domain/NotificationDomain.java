package nct.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [알림 - 알림도메인 코드]
 * - 알림함 필터 탭의 기준이 되는 공통코드(NTFG03)
 * - 현재 DB에는 4종(경매/거래/서비스/운영)만 존재 — 프론트의 '채팅' 탭은 대응 코드가 없음 (팀 결정 대기)
 */
@Getter
@RequiredArgsConstructor
public enum NotificationDomain {

    AUCTION("NTFC0010"),
    TRADE("NTFC0011"),
    SERVICE("NTFC0012"),
    OPS("NTFC0013");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
