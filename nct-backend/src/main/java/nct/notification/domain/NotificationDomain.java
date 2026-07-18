package nct.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [알림 - 알림도메인 코드]
 * - 알림함 필터 탭의 기준이 되는 공통코드(NTFG03)
 * - 채팅(NTFC0014)은 팀 결정(2026-07-17)으로 추가 — DB 공통코드 반영 SQL은
 *   팀전달_NTFG03_채팅코드추가_260717.md 참조. 채팅 알림 발행은 채팅 기능 담당자 몫
 */
@Getter
@RequiredArgsConstructor
public enum NotificationDomain {

    AUCTION("NTFC0010"),
    TRADE("NTFC0011"),
    SERVICE("NTFC0012"),
    OPS("NTFC0013"),
    CHAT("NTFC0014");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
}
