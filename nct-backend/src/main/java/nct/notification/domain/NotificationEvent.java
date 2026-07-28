package nct.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Claude Code 작성 (BJN, 2026-07-24)
 *
 * [알림 - 알림 세부 이벤트 코드] (F-COM-012 세분화, 목업 34_notification_settings.html 기준)
 * - USER_NOTIFICATION_EVENT_SETTING.NTF_EVT_CD (NTFG05, 정본 반영 대기 — 팀전달_알림설정_운영이벤트연동및코드신설요청_260724.md)
 * - 기존 도메인 단위(경매/거래/서비스, USER_NOTIFICATION_SETTING) 설정 화면은 그대로 두고
 *   이 이벤트 단위 설정은 별도 신규 테이블로 추가한다 — 기존 이메일 발송 계약(F-COM-006, D-031)에
 *   영향 주지 않기 위해서다. 경매/거래(배송시작 제외)/서비스/운영 일부는 알림 발행 호출 자체가
 *   아직 없어(경매·서비스는 담당자5, 신고·심사·공지는 담당자7) 그쪽 팀전달 문서로 호출 추가를 요청했다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationEvent {

    BID_UPDATED("NTFC0017", NotificationDomain.AUCTION, NotificationType.BID, "입찰가 갱신"),
    AUCTION_CLOSING_SOON("NTFC0018", NotificationDomain.AUCTION, NotificationType.AUCTION, "마감 임박"),
    AUCTION_RESULT("NTFC0019", NotificationDomain.AUCTION, NotificationType.AUCTION, "낙찰/유찰 결과"),

    DELIVERY_START("NTFC0020", NotificationDomain.TRADE, NotificationType.TRADE, "배송 시작"),
    TRADE_CONFIRM_REQUEST("NTFC0021", NotificationDomain.TRADE, NotificationType.TRADE, "거래 확정 요청"),
    TRADE_COMPLETE("NTFC0022", NotificationDomain.TRADE, NotificationType.TRADE, "거래 완료"),

    NEW_QUOTE("NTFC0023", NotificationDomain.SERVICE, NotificationType.SERVICE, "새 견적 도착"),
    QUOTE_SELECTED("NTFC0024", NotificationDomain.SERVICE, NotificationType.SERVICE, "견적 선택됨"),
    SERVICE_COMPLETE("NTFC0025", NotificationDomain.SERVICE, NotificationType.SERVICE, "서비스 완료"),

    POINT_CHANGE("NTFC0026", NotificationDomain.OPS, NotificationType.OPS, "포인트 지급/차감"),
    PROVIDER_APPROVAL_RESULT("NTFC0027", NotificationDomain.OPS, NotificationType.OPS, "제공자 심사 결과"),
    REPORT_RESULT("NTFC0028", NotificationDomain.OPS, NotificationType.OPS, "신고 처리 결과"),
    NOTICE_PUBLISHED("NTFC0029", NotificationDomain.OPS, NotificationType.OPS, "공지사항");

    /** DB에 저장되는 공통코드 값 */
    private final String code;
    /** 설정 화면에서 도메인별로 묶어 보여주기 위한 소속 도메인 */
    private final NotificationDomain domain;
    /** 알림함 배지 분류(NTFG01) — build()에 그대로 전달 */
    private final NotificationType type;
    /** 설정 화면 표시명 (목업 문구 그대로) */
    private final String label;
}
