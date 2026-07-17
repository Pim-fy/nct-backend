package nct.notification.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;
import nct.notification.domain.Notification;

/**
 * [알림 - 알림 행 응답 DTO]
 * - GET /api/notification 응답 본문의 배열 원소
 * - 읽음여부는 DB의 'Y'/'N' 문자를 boolean으로 변환해 내려준다 (프론트가 바로 조건문에 사용)
 * - 상대시간("N분 전") 변환은 프론트 담당 — 서버는 ISO 형식 regDt만 제공
 */
@Getter
@Builder
public class NotificationResponse {

    private final Long id;

    /** 알림유형 코드/한글명 (예: NTFC0002 / 입찰) */
    private final String typeCd;
    private final String type;

    /** 알림도메인 코드/한글명 — 필터 그룹핑은 프론트가 domainCd 기준으로 수행 */
    private final String domainCd;
    private final String domain;

    private final String title;
    private final String content;

    private final String refTypeCd;
    private final Long refSn;

    /** 읽음 여부 (true=읽음) */
    private final boolean read;
    private final LocalDateTime readDt;
    private final LocalDateTime regDt;

    /** 도메인 모델 → 응답 DTO 변환 */
    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getNtfSn())
                .typeCd(n.getNtfTypeCd())
                .type(n.getTypeNm())
                .domainCd(n.getNtfDomainCd())
                .domain(n.getDomainNm())
                .title(n.getNtfTtl())
                .content(n.getNtfCn())
                .refTypeCd(n.getNtfRefTypeCd())
                .refSn(n.getNtfRefSn())
                .read("Y".equals(n.getNtfReadYn()))
                .readDt(n.getNtfReadDt())
                .regDt(n.getNtfRegDt())
                .build();
    }
}
