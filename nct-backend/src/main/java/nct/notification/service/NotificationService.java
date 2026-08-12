package nct.notification.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.global.security.crypto.FieldCryptoService;
import nct.notification.domain.Notification;
import nct.notification.domain.NotificationAudience;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationEmailStatus;
import nct.notification.domain.NotificationEvent;
import nct.notification.domain.NotificationType;
import nct.notification.domain.UserNotificationEventSetting;
import nct.notification.domain.UserNotificationSetting;
import nct.notification.dto.NotificationResponse;
import nct.notification.mapper.NotificationMapper;
import nct.notification.mapper.UserNotificationEventSettingMapper;
import nct.notification.mapper.UserNotificationSettingMapper;
import nct.setting.mapper.SystemSettingAdminMapper;

/**
 * [알림 - 서비스 계약] (담당자6 백종남, F-UX-064/065)
 *
 * 다른 도메인은 이벤트 발생 시 notify(...)만 호출한다 — NOTIFICATION 테이블 직접 INSERT 금지.
 * 호출하는 쪽 트랜잭션 안에서 부르면 같은 트랜잭션으로 묶인다 (본 이벤트 실패 시 알림도 함께 롤백).
 *
 * 이메일 보조 발송 (F-COM-006, 트리거 확정 2026-07-18):
 * - 대상 이벤트: 거래 완료 확인 요청 / 거래 문제(분쟁) 접수·판정 / 환전 지급·반려 —
 *   "거래 완료와 분쟁에 직접 영향을 주는 중요 이벤트"만. 입찰가 갱신 같은 고빈도 알림은 인앱만.
 * - 발송 조건: 시스템 설정 이메일 스위치(Y) AND 회원의 도메인별 이메일 수신 토글(Y).
 *   OPS 도메인(환전 등 돈 지급 결과)은 회원 토글이 없어 전역 스위치만 본다.
 * - 베스트 에포트: 이메일이 실패해도 인앱 알림·본 처리는 그대로 성공하고, 결과만
 *   NTF_EMAIL_STATUS_CD(성공/실패/미대상)에 기록한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserNotificationSettingMapper settingMapper;
    private final UserNotificationEventSettingMapper eventSettingMapper;
    private final SystemSettingAdminMapper systemSettingMapper;
    private final NotificationMailSender mailSender;
    private final NotificationEventPublisher eventPublisher;
    // @ai_generated: USERS 이메일 암호문은 실제 이메일 발송 직전에만 복호화한다.
    private final FieldCryptoService fieldCryptoService;

    /**
     * 범용 알림 생성 (모든 알림의 단일 진입점).
     * 대상 구분을 지정하지 않는 기존 시그니처 — 일반 활동 알림으로 기록된다 (F-COM-011 분류표 기본값).
     *
     * @param refType 알림을 발생시킨 참조 유형 (없으면 null 허용)
     * @param refSn   참조 일련번호
     */
    @Transactional
    public void notify(long usrSn, NotificationType type, NotificationDomain domain,
                       String title, String content, RefType refType, Long refSn) {
        notify(usrSn, type, domain, NotificationAudience.GENERAL, title, content, refType, refSn);
    }

    /**
     * 범용 알림 생성 — 대상 구분(일반/제공자)까지 지정하는 버전 (F-COM-011).
     * 제공자 업무 알림(정산·견적 요청 등)을 발행할 때는 이 시그니처로 PROVIDER를 지정한다.
     * 어떤 알림이 어느 쪽인지는 분류표(팀전달_알림구분_260717.md)를 따른다.
     */
    @Transactional
    public void notify(long usrSn, NotificationType type, NotificationDomain domain,
                       NotificationAudience audience, String title, String content,
                       RefType refType, Long refSn) {
        Notification n = build(usrSn, type, domain, audience, title, content, refType, refSn);
        insertInappOnly(n, usrSn);
    }

    /**
     * 중요 이벤트 알림 — 인앱 알림 + 이메일 보조 발송 (F-COM-006).
     * 트리거 3종(거래 완료 확인 요청·분쟁 접수/판정·환전 지급/반려)에서만 호출한다.
     * 이메일 결과와 무관하게 인앱 알림은 항상 생성된다 (베스트 에포트).
     */
    @Transactional
    public void notifyImportant(long usrSn, NotificationType type, NotificationDomain domain,
                                NotificationAudience audience, String title, String content,
                                RefType refType, Long refSn) {
        Notification n = build(usrSn, type, domain, audience, title, content, refType, refSn);

        // 발송 대상이 아니면(스위치·토글·메일 미설정 환경) 미대상으로 기록하고 인앱만 남긴다
        if (!emailEligible(usrSn, domain)) {
            insertInappOnly(n, usrSn);
            return;
        }
        insertWithEmail(n, usrSn, title, content);
    }

    // 이메일 보조 발송 공통 문구 — notifyImportant/notifyForEvent 두 경로가 같은 형식을 쓴다
    // (예전엔 두 메서드에 문자열이 하드코딩으로 중복돼 있었다 — 2026-08-05 점검 후속 통합)
    private static final String EMAIL_SUBJECT_PREFIX = "[에누리컷] ";
    private static final String EMAIL_FOOTER =
            "\n\n자세한 내용은 에누리컷 알림함에서 확인해 주세요. (본 메일은 발신 전용입니다)";

    /** 이메일 없이 인앱 알림만 기록 — notify()·이메일 미대상 경로 3곳이 공유 (2026-08-05 중복 통합) */
    private void insertInappOnly(Notification n, long usrSn) {
        n.setNtfEmailStatusCd(NotificationEmailStatus.NONE.getCode());
        notificationMapper.insert(n);
        eventPublisher.publishAfterCommit(usrSn, NotificationResponse.from(n));
    }

    /**
     * 알림 행 기록 + 이메일 보조 발송 공통 흐름 — notifyImportant/notifyForEvent가 공유
     * (같은 20여 줄이 두 메서드에 중복돼 있던 것을 추출, 2026-08-05 점검 후속).
     * 대기 상태로 먼저 기록 → 발송 시도 → 결과(성공/실패)로 갱신.
     * 발송기(mailSender)는 예외를 던지지 않는 계약이고, 이메일 복호화 등 예기치 못한 실패도
     * 여기서 삼켜 인앱 알림과 원래 업무 트랜잭션을 깨뜨리지 않는다 (베스트 에포트).
     */
    private void insertWithEmail(Notification n, long usrSn, String title, String content) {
        n.setNtfEmailStatusCd(NotificationEmailStatus.PENDING.getCode());
        notificationMapper.insert(n);
        eventPublisher.publishAfterCommit(usrSn, NotificationResponse.from(n));

        boolean sent = false;
        try {
            String email = fieldCryptoService.decrypt(notificationMapper.selectUserEmail(usrSn));
            sent = email != null
                    && mailSender.send(email, EMAIL_SUBJECT_PREFIX + title, content + EMAIL_FOOTER);
        } catch (RuntimeException ignored) {
            // 이메일 복호화·발송 실패는 인앱 알림과 원래 업무 트랜잭션을 취소하지 않는다.
        }
        notificationMapper.updateEmailStatus(n.getNtfSn(),
                (sent ? NotificationEmailStatus.SENT : NotificationEmailStatus.FAILED).getCode());
    }

    /** 알림 행 공통 조립 — notify/notifyImportant가 공유 (이메일 상태만 호출부가 결정) */
    private Notification build(long usrSn, NotificationType type, NotificationDomain domain,
                               NotificationAudience audience, String title, String content,
                               RefType refType, Long refSn) {
        Notification n = new Notification();
        n.setUsrSn(usrSn);
        n.setNtfTypeCd(type.getCode());
        n.setNtfDomainCd(domain.getCode());
        n.setNtfAudienceCd(audience.getCode());
        n.setNtfTtl(title);
        n.setNtfCn(content);
        n.setNtfRefTypeCd(refType != null ? refType.getCode() : null);
        n.setNtfRefSn(refSn);
        return n;
    }

    /**
     * 이메일 보조 발송 대상인지 판정 (F-COM-006 발송 조건).
     * ① 메일 설정이 있는 환경이고 ② 관리자 전역 스위치(SYS_SET_EMAIL_YN)가 Y이고
     * ③ 회원의 해당 도메인 이메일 토글이 Y여야 한다.
     * OPS(환전 등 지갑·운영)·CHAT은 알림 설정 화면에 이메일 토글이 없는 도메인이라 ③을 건너뛴다.
     */
    private boolean emailEligible(long usrSn, NotificationDomain domain) {
        if (!mailSender.isAvailable()) {
            return false;
        }
        if (!"Y".equals(systemSettingMapper.selectOne().getEmailYn())) {
            return false;
        }
        UserNotificationSetting setting = settingMapper.selectByUser(usrSn);
        if (setting == null) {
            setting = UserNotificationSetting.defaultOf(usrSn); // 저장한 적 없으면 전 채널 수신이 기본
        }
        return switch (domain) {
            case AUCTION -> "Y".equals(setting.getUsrNtfStgAucEmailYn());
            case TRADE -> "Y".equals(setting.getUsrNtfStgTrdEmailYn());
            case SERVICE -> "Y".equals(setting.getUsrNtfStgSvcEmailYn());
            case OPS, CHAT -> true; // 토글 없는 도메인 — 전역 스위치만 적용
        };
    }

    // ================================================================
    // 알림 이벤트별 설정 (F-COM-012 세분화, 2026-07-24, 2026-07-28 테이블 실DB 적용 확인 후 전환)
    // 위 도메인 단위(경매/거래/서비스) 설정·emailEligible()은 그대로 두고, 이벤트 단위 설정을
    // USER_NOTIFICATION_EVENT_SETTING(신규)에 별도로 추가한다.
    //
    // 신규 테이블이 공유 DB(NCTDB)에 적용된 걸 확인해, 기존에 도메인 단위 게이팅으로 남겨뒀던
    // "거래 확정 요청"(notifyTradeConfirmRequest)·"포인트 지급/차감"(notifyCharge/notifyExchangeRequest/
    // notifyPointConvert)도 notifyForEvent(TRADE_CONFIRM_REQUEST / POINT_CHANGE)로 교체했다.
    // "분쟁 접수/판정"·"환전 지급/반려"는 이벤트 목록에 아예 없거나(분쟁) D-031 정책상 의도적으로
    // 게이팅하지 않는 것(환전 지급/반려)이라 기존 emailEligible() 경로를 계속 쓴다(§ 알림 수신 설정
    // 섹션 참고 — 도메인 단위 설정 화면이 없어졌으니 사실상 고정값).
    // ================================================================

    /** 내 이벤트별 설정 전체 조회 — NotificationEvent 전체, 저장한 적 없는 이벤트는 기본값(전 채널 수신) */
    public List<UserNotificationEventSetting> getEventSettings(long usrSn) {
        Map<String, UserNotificationEventSetting> saved = eventSettingMapper.selectByUser(usrSn).stream()
                .collect(Collectors.toMap(UserNotificationEventSetting::getNtfEvtCd, Function.identity()));
        return Arrays.stream(NotificationEvent.values())
                .map(e -> saved.getOrDefault(e.getCode(), UserNotificationEventSetting.defaultOf(usrSn, e.getCode())))
                .toList();
    }

    /**
     * 내 이벤트별 설정 저장 — 화면이 보낸 항목만큼 업서트(누락된 이벤트는 손대지 않음).
     * USER_NOTIFICATION_EVENT_SETTING.NTF_EVT_CD는 CMM_CODE FK가 걸려 있어, 모르는 코드로
     * upsert하면 FK 위반이 SQL 예외(500)로 그대로 터진다 — 등록 안 된 이벤트 코드를 enum에 먼저
     * 넣고 배포했다가 전체 회원 저장이 막혔던 사고(2026-08-04) 재발 방지로, DB까지 가기 전에
     * 알려진 이벤트 코드인지 앱 계층에서 먼저 검증한다(D-009 원칙 그대로 적용).
     */
    @Transactional
    public void saveEventSettings(long usrSn, List<UserNotificationEventSetting> settings) {
        Set<String> knownCodes = Arrays.stream(NotificationEvent.values())
                .map(NotificationEvent::getCode)
                .collect(Collectors.toSet());
        settings.forEach(s -> {
            if (!knownCodes.contains(s.getNtfEvtCd())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        });
        settings.forEach(s -> {
            s.setUsrSn(usrSn);
            eventSettingMapper.upsert(s);
        });
    }

    private boolean eventInappEnabled(long usrSn, NotificationEvent event) {
        UserNotificationEventSetting s = eventSettingMapper.selectByUserAndEvent(usrSn, event.getCode());
        return s == null || "Y".equals(s.getUsrNtfEvtStgInappYn()); // 행 없으면 기본 수신(Y)
    }

    private boolean eventEmailEnabled(long usrSn, NotificationEvent event) {
        UserNotificationEventSetting s = eventSettingMapper.selectByUserAndEvent(usrSn, event.getCode());
        return s == null || "Y".equals(s.getUsrNtfEvtStgEmailYn());
    }

    /**
     * 이벤트 단위 알림 발행 — 위 notify()/notifyImportant()와 달리 이벤트별 설정으로 게이팅한다.
     * 인앱을 꺼두면 이메일도 함께 나가지 않는다 — 이메일 발송 결과(NTF_EMAIL_STATUS_CD)를
     * 알림 행에 같이 기록하는 현재 구조상 행 자체를 안 만들면 이메일만 따로 보낼 수 없기 때문
     * (인앱·이메일을 완전히 독립적으로 켜고 끄려면 이메일 발송 이력을 별도 테이블로 분리해야 함 — 이번 범위 아님).
     */
    @Transactional
    public void notifyForEvent(long usrSn, NotificationEvent event, NotificationAudience audience,
                               String title, String content, RefType refType, Long refSn) {
        // 멱등 체크 — 같은 회원·같은 이벤트·같은 참조 건으로 이미 발행했으면 조용히 스킵.
        // 마감임박 알림처럼 매분 도는 스케줄러가 같은 경매를 반복 호출해도 알림이 한 번만 가게 하기 위함.
        // refSn이 없는 알림(참조 건 없음)은 중복 판단 기준이 없으므로 체크하지 않는다.
        if (refSn != null && notificationMapper.existsByUserEventRef(usrSn, event.getCode(), refSn)) {
            return;
        }
        if (!eventInappEnabled(usrSn, event)) {
            return;
        }
        Notification n = build(usrSn, event.getType(), event.getDomain(), audience, title, content, refType, refSn);
        n.setNtfEvtCd(event.getCode()); // 멱등 체크 키 — notifyForEvent 경로만 채운다 (기존 notify()는 null 유지)

        boolean emailEligible = mailSender.isAvailable()
                && "Y".equals(systemSettingMapper.selectOne().getEmailYn())
                && eventEmailEnabled(usrSn, event);
        if (!emailEligible) {
            insertInappOnly(n, usrSn);
            return;
        }
        insertWithEmail(n, usrSn, title, content);
    }

    // ---------- 신규 이벤트 발행 계약 (2026-07-24) — 경매(담당자5)·거래(담당자4)·서비스(담당자5)·
    // 운영(담당자7) 쪽에 호출 추가를 요청함(팀전달_알림설정_*_260724.md 3건).
    // 연결 현황 (2026-08-07 확인): 입찰가 갱신·마감임박(경매), 배송 시작·거래 완료(거래),
    // 새 채팅 메시지(채팅), 제공자 승인 결과(제공자)는 호출 연결 완료. 견적 도착/선택·서비스 완료·
    // 공지 발행·분쟁 접수/판정 6종은 아직 담당 파트 호출 대기 상태 ----------

    /** 입찰가 갱신 — 경매/입찰 담당(5)이 새 최고 입찰 발생 시 호출 (대상: 밀려난 이전 최고 입찰자 등) */
    public void notifyBidUpdated(long usrSn, long auctionId, long newPrice) {
        notifyForEvent(usrSn, NotificationEvent.BID_UPDATED, NotificationAudience.GENERAL,
                "입찰가가 갱신되었습니다",
                String.format("관심 경매의 입찰가가 %,dP로 갱신되었습니다.", newPrice),
                RefType.AUCTION, auctionId);
    }

    /** 마감 임박(10분 전) — 경매 담당(5)이 스케줄러 등에서 호출 */
    public void notifyAuctionClosingSoon(long usrSn, long auctionId) {
        notifyForEvent(usrSn, NotificationEvent.AUCTION_CLOSING_SOON, NotificationAudience.GENERAL,
                "마감이 임박했습니다",
                "관심 경매가 10분 후 마감됩니다.",
                RefType.AUCTION, auctionId);
    }

    /** 낙찰/유찰 결과 — 경매 담당(5)이 마감 처리 시 호출 */
    public void notifyAuctionResult(long usrSn, long auctionId, boolean won) {
        String title = auctionResultTitle(auctionId, won ? "낙찰되었습니다" : "유찰되었습니다");
        notifyForEvent(usrSn, NotificationEvent.AUCTION_RESULT, NotificationAudience.GENERAL,
                title,
                won ? "축하합니다! 입찰하신 경매에 낙찰되었습니다." : "입찰하신 경매가 유찰되었습니다.",
                RefType.AUCTION, auctionId);
    }

    /**
     * 무입찰 유찰 — 판매자에게. 경매 담당(5)이 입찰자 0명으로 마감됐을 때 호출.
     * 위 notifyAuctionResult(입찰자 대상)와 같은 AUCTION_RESULT 이벤트를 재사용 — 신규 코드 불필요.
     * 멱등 키가 (회원, 이벤트, 참조)라서 같은 경매여도 판매자·입찰자는 회원이 달라 충돌하지 않는다.
     */
    public void notifyAuctionFailed(long sellerUsrSn, long auctionId) {
        notifyForEvent(sellerUsrSn, NotificationEvent.AUCTION_RESULT, NotificationAudience.GENERAL,
                auctionResultTitle(auctionId, "경매가 유찰되었습니다"),
                "입찰자가 없어 경매가 종료되었습니다.",
                RefType.AUCTION, auctionId);
    }

    /** 알림 목록에서 제목만 보고도 어떤 경매인지 알 수 있도록 상품명을 앞에 붙인다 (상품 삭제 등으로 조회가 안 되면 상품명 없이 둔다) */
    private String auctionResultTitle(long auctionId, String suffix) {
        String productName = notificationMapper.selectAuctionProductName(auctionId);
        return productName == null ? suffix : "[" + productName + "] " + suffix;
    }

    /**
     * 구매자 문의 등록 — 상품 담당(2, 신현석)이 addInquiry 완료 시 호출. 대상: 판매자.
     * refSn = prdCmtSn — 구매자가 같은 상품에 새 문의를 여러 번 등록할 수 있어 멱등 키를
     * prdSn이 아닌 문의 단건 기준으로 잡아야 매 문의마다 알림이 발송된다 (신현석 요청, 2026-07-29).
     */
    public void notifyInquiryReceived(long sellerUsrSn, long prdCmtSn) {
        notifyForEvent(sellerUsrSn, NotificationEvent.INQUIRY_RECEIVED, NotificationAudience.GENERAL,
                "새 구매자 문의가 등록되었습니다",
                "등록하신 상품에 새 문의가 도착했습니다.",
                RefType.PRODUCT, prdCmtSn);
    }

    /**
     * 판매자 답변 등록 — 상품 담당(2, 신현석)이 addReply 완료 시 호출. 대상: 문의 작성 구매자.
     * refSn = prdCmtSn — 답변(reply) 행 자신의 prdCmtSn 기준 (2026-07-29).
     */
    public void notifyInquiryReplied(long buyerUsrSn, long prdCmtSn) {
        notifyForEvent(buyerUsrSn, NotificationEvent.INQUIRY_REPLIED, NotificationAudience.GENERAL,
                "판매자가 문의에 답변했습니다",
                "등록하신 문의에 판매자가 답변 하였습니다.",
                RefType.PRODUCT, prdCmtSn);
    }

    /**
     * 새 채팅 메시지 — 채팅 담당(4, 정민재)이 메시지 저장 후 호출 (대상: 상대방).
     * "새 채팅 메시지"(NTFC0032) 이벤트로 게이팅한다. CMM_CODE(NTFG05, CMM_SN=233) 반영 완료
     * 확인(조우진, 2026-08-04) 후 재반영 — 실제 호출 연결은 여전히 채팅 담당 몫, 미호출 상태.
     * refType/refSn은 일부러 비워둔다 — TRADE+tradeId로 잡으면 notifyForEvent의 (회원·이벤트·참조)
     * 멱등 체크가 같은 거래방의 두 번째 메시지부터 계속 막아버린다(거래당 1회성 이벤트인
     * DELIVERY_START류와 달리 채팅은 같은 참조로 반복 발생). 메시지 단위로 멱등 키를 잡으려면
     * CHAT_MESSAGE 참조유형(RefType) 신설이 필요해 이번 범위에서는 뺐다.
     */
    public void notifyChatMessage(long usrSn) {
        notifyForEvent(usrSn, NotificationEvent.NEW_CHAT_MESSAGE, NotificationAudience.GENERAL,
                "새 채팅 메시지",
                "채팅방에 새 메시지가 도착했습니다.",
                null, null);
    }

    /** 배송 시작 — 거래/배송 담당(4)이 배송 상태 전이 시 호출 (대상: 구매자) */
    public void notifyDeliveryStart(long usrSn, long tradeId) {
        notifyForEvent(usrSn, NotificationEvent.DELIVERY_START, NotificationAudience.GENERAL,
                "배송이 시작되었습니다",
                "주문하신 상품의 배송이 시작되었습니다.",
                RefType.TRADE, tradeId);
    }

    /** 거래 완료(수동/자동 공용) — 거래 담당(4)이 호출. 기존 자동완료 직접 notify() 호출을 이걸로 교체 요청 */
    public void notifyTradeComplete(long usrSn, long tradeId, boolean auto) {
        notifyForEvent(usrSn, NotificationEvent.TRADE_COMPLETE, NotificationAudience.GENERAL,
                auto ? "거래 자동 완료" : "거래 완료",
                auto ? "상대방 확인 기한이 지나 거래가 자동으로 완료되었습니다." : "거래가 완료되었습니다.",
                RefType.TRADE, tradeId);
    }

    /** 관리자 판매자 취소 승인 결과를 거래 양 당사자에게 알린다. */
    public void notifyTradeCancelled(long usrSn, long tradeId, boolean buyer) {
        notify(usrSn, NotificationType.TRADE, NotificationDomain.TRADE,
                "거래가 취소되었습니다",
                buyer
                        ? "판매자 취소 요청이 승인되어 거래대금이 환불되었습니다."
                        : "판매자 취소 요청이 승인되어 거래가 취소되었습니다.",
                RefType.TRADE, tradeId);
    }

    /** 서비스 거래 당사자의 상호 동의 취소 결과를 각 역할에 맞게 알린다. */
    public void notifyServiceTradeCancelled(long usrSn, long tradeId, boolean requester) {
        notify(usrSn, NotificationType.TRADE, NotificationDomain.TRADE,
                "서비스 거래가 취소되었습니다",
                requester
                        ? "상대방이 일정 취소 요청에 동의하여 보관금이 전액 환불되었습니다."
                        : "일정 취소 요청에 동의하여 서비스 거래가 취소되었습니다.",
                RefType.TRADE, tradeId);
    }

    /** 새 견적 도착 — 서비스 요청자에게, 서비스 매칭 담당(5, 2단계)이 호출 */
    public void notifyNewQuote(long usrSn, long requestId) {
        notifyForEvent(usrSn, NotificationEvent.NEW_QUOTE, NotificationAudience.GENERAL,
                "새 견적이 도착했습니다",
                "등록하신 서비스 요청에 새 견적이 도착했습니다.",
                RefType.SERVICE_REQUEST, requestId);
    }

    /** 견적 선택됨 — 견적 제출한 제공자에게, 서비스 매칭 담당(5, 2단계)이 호출 */
    public void notifyQuoteSelected(long usrSn, long quoteId) {
        notifyForEvent(usrSn, NotificationEvent.QUOTE_SELECTED, NotificationAudience.PROVIDER,
                "견적이 선택되었습니다",
                "제출하신 견적이 선택되었습니다.",
                RefType.QUOTE, quoteId);
    }

    /** 서비스 완료 — 서비스 매칭 담당(5, 2단계)이 호출 */
    public void notifyServiceComplete(long usrSn, long serviceTradeId) {
        notifyForEvent(usrSn, NotificationEvent.SERVICE_COMPLETE, NotificationAudience.GENERAL,
                "서비스가 완료되었습니다",
                "이용하신 서비스 거래가 완료되었습니다.",
                RefType.TRADE, serviceTradeId);
    }

    /** 제공자 심사 결과 — 제공자 신청 담당(7)이 승인/반려 확정 시 호출 */
    public void notifyProviderApprovalResult(long usrSn, boolean approved, String reason) {
        notifyForEvent(usrSn, NotificationEvent.PROVIDER_APPROVAL_RESULT, NotificationAudience.GENERAL,
                approved ? "제공자 신청이 승인되었습니다" : "제공자 신청이 반려되었습니다",
                approved ? "제공자 신청이 승인되었습니다. 이제 제공자 모드를 이용하실 수 있습니다."
                        : "제공자 신청이 반려되었습니다. 사유: " + reason,
                null, null);
    }

    /** 신고 처리 결과 — 신고 처리 담당(7)이 처리 확정 시 호출 */
    public void notifyReportResult(long usrSn, long reportId, String result) {
        notifyForEvent(usrSn, NotificationEvent.REPORT_RESULT, NotificationAudience.GENERAL,
                "신고 처리 결과",
                "접수하신 신고가 처리되었습니다. 결과: " + result,
                null, null);
    }

    /**
     * 공지사항 게시 — 공지 담당(7)이 호출. 특정 회원이 아니라 "게시됨" 자체를 구독자에게
     * 알리는 성격이라 대상 판단(전체 발송 vs 개별)은 방식을 먼저 상의하기로 함 — 시그니처 미확정.
     * (팀전달_알림설정_운영이벤트연동및코드신설요청_260724.md §3 참고)
     */
    public void notifyNoticePublished(long usrSn, long noticeId, String noticeTitle) {
        notifyForEvent(usrSn, NotificationEvent.NOTICE_PUBLISHED, NotificationAudience.GENERAL,
                "새 공지사항",
                noticeTitle,
                RefType.NOTICE, noticeId);
    }

    // ---------- 중요 이벤트 트리거 계약 (F-COM-006) — 거래·분쟁 담당자(4·5)가 호출 ----------

    /**
     * 거래 완료 확인 요청 — 상대방 확인 대기 시작 시 거래 담당자(4)가 호출. 기한 내 미확인 시 자동완료.
     * "거래 확정 요청"(NTFC0021) 이벤트로 게이팅한다 — 이벤트 목록의 type이 TRADE라
     * 알림 배지가 기존 [OPS]에서 [거래]로 바뀐다(F-COM-012 재분류, 의도된 변경).
     */
    public void notifyTradeConfirmRequest(long usrSn, long trdSn, int confirmDays) {
        notifyForEvent(usrSn, NotificationEvent.TRADE_CONFIRM_REQUEST, NotificationAudience.GENERAL,
                "거래 완료 확인 요청",
                String.format("상대방이 거래 완료 확인을 기다리고 있습니다. %d일 안에 확인하지 않으면 자동으로 완료 처리됩니다.", confirmDays),
                RefType.TRADE, trdSn);
    }

    /** 거래 문제(분쟁) 접수 알림 — 접수 시 상대 당사자에게, 분쟁 담당자(5)가 호출 */
    public void notifyDisputeReceived(long usrSn, long trdDspSn) {
        notifyImportant(usrSn, NotificationType.OPS, NotificationDomain.TRADE, NotificationAudience.GENERAL,
                "거래 문제 접수",
                "회원님이 당사자인 거래에 거래 문제가 접수되었습니다. 처리 완료 전까지 관련 정산·포인트 전환이 보류될 수 있습니다.",
                RefType.TRADE_DISPUTE, trdDspSn);
    }

    /** 거래 문제(분쟁) 판정 결과 알림 — 처리 완료/반려 시 양 당사자에게, 분쟁 담당자(5)가 호출 */
    public void notifyDisputeResolved(long usrSn, long trdDspSn, String resultText) {
        notifyImportant(usrSn, NotificationType.OPS, NotificationDomain.TRADE, NotificationAudience.GENERAL,
                "거래 문제 처리 결과",
                "접수된 거래 문제가 처리되었습니다. 결과: " + resultText,
                RefType.TRADE_DISPUTE, trdDspSn);
    }

    /** 포인트 홀딩 반환 알림 (업무분장: 입찰·낙찰·반환 알림) — PointService.releaseHold가 호출 */
    public void notifyPointRelease(long usrSn, long amt, RefType refType, long refSn, String reason) {
        notify(usrSn, NotificationType.BID, NotificationDomain.AUCTION,
                "포인트 반환",
                String.format("%,dP가 사용 가능 포인트로 반환되었습니다. (%s)", amt, reason),
                refType, refSn);
    }

    /** 정산 이벤트 알림 — SettlementService가 호출. 판매대금을 받는 쪽(제공자 업무)이라 PROVIDER */
    public void notifySettlement(long usrSn, String title, String content, long trdSn) {
        notify(usrSn, NotificationType.TRADE, NotificationDomain.TRADE,
                NotificationAudience.PROVIDER, title, content, RefType.TRADE, trdSn);
    }

    /**
     * 포인트 충전 완료 알림 — PointChargeService가 호출.
     * "포인트 지급/차감"(NTFC0026) 이벤트로 게이팅한다.
     */
    public void notifyCharge(long usrSn, long amt) {
        notifyForEvent(usrSn, NotificationEvent.POINT_CHANGE, NotificationAudience.GENERAL,
                "충전 완료",
                String.format("%,dP가 충전되었습니다.", amt),
                null, null);
    }

    /**
     * 환전 신청 접수 알림 — PointExchangeService가 호출 (F-PAY-012, D-026)
     * 실제 입금은 관리자 수동 처리라 "며칠 내 지급 예정" 안내만 나간다 (자동화 금지 정본 규칙)
     * 위 notifyCharge와 같은 사유로 "포인트 지급/차감"(NTFC0026) 이벤트로 게이팅한다.
     */
    public void notifyExchangeRequest(long usrSn, long amt) {
        notifyForEvent(usrSn, NotificationEvent.POINT_CHANGE, NotificationAudience.GENERAL,
                "환전 신청 접수",
                String.format("%,dP 환전 신청이 접수되었습니다. 등록하신 계좌로 며칠 내 지급될 예정입니다.", amt),
                null, null);
    }

    /**
     * 환전 지급 완료 알림 — 실제 돈이 오간 결과라 이메일 보조 발송 대상 (F-COM-006 트리거, D-031).
     * D-031은 "OPS(환전 등 돈 지급 결과)는 회원 토글 없이 전역 스위치만 적용"으로 확정된 정책이라,
     * 이벤트별 설정(NTFC0026)을 새로 추가했어도 이 알림만은 일부러 게이팅하지 않는다 — 이벤트 인앱
     * 체크박스를 껐다고 실제 지급 완료 이메일까지 조용히 막히면 D-031 취지에 어긋나기 때문.
     */
    public void notifyExchangeComplete(long usrSn, long amt) {
        notifyImportant(usrSn, NotificationType.OPS, NotificationDomain.OPS, NotificationAudience.GENERAL,
                "환전 지급 완료",
                String.format("%,dP 환전 지급이 완료되었습니다. 등록하신 계좌를 확인해 주세요.", amt),
                null, null);
    }

    /**
     * 포인트 전환 완료 알림 (F-PAY-010) — 본인 지갑 관리 이벤트라 '일반' 구분(기본값)으로 나간다.
     * 위 notifyCharge와 같은 사유로 "포인트 지급/차감"(NTFC0026) 이벤트로 게이팅한다.
     */
    public void notifyPointConvert(long usrSn, long amt) {
        notifyForEvent(usrSn, NotificationEvent.POINT_CHANGE, NotificationAudience.GENERAL,
                "포인트 전환 완료",
                String.format("정산 가능 포인트 %,dP가 사용 가능 포인트로 전환되었습니다.", amt),
                null, null);
    }

    /** 보관금 정산 적립 알림 (F-SVC-015) — PointService.creditEscrowToSettleable가 호출. 대금을 받는 쪽(제공자 업무)이라 PROVIDER */
    public void notifyEscrowSettled(long usrSn, long amt, RefType refType, long refSn) {
        notify(usrSn, NotificationType.TRADE, NotificationDomain.TRADE,
                NotificationAudience.PROVIDER,
                "정산 가능 포인트 적립",
                String.format("거래대금 %,dP가 정산 가능 포인트로 적립되었습니다.", amt),
                refType, refSn);
    }

    /** 분쟁 판정 보관금 환불 알림 — PointService.refundEscrow가 호출. 판정 내용 이메일은 notifyDisputeResolved(분쟁 담당자 호출) 몫이라 여기서는 인앱만 */
    public void notifyPointRefund(long usrSn, long amt, RefType refType, long refSn, String reason) {
        notify(usrSn, NotificationType.TRADE, NotificationDomain.TRADE,
                "포인트 환불",
                String.format("%,dP가 사용 가능 포인트로 환불되었습니다. (%s)", amt, reason),
                refType, refSn);
    }

    /**
     * 환전 반려 알림 — 실제 돈이 오간 결과라 이메일 보조 발송 대상 (F-COM-006 트리거, D-031).
     * notifyExchangeComplete와 같은 이유로 이벤트별 설정(NTFC0026)으로 게이팅하지 않는다.
     */
    public void notifyExchangeReject(long usrSn, long amt, String reason) {
        notifyImportant(usrSn, NotificationType.OPS, NotificationDomain.OPS, NotificationAudience.GENERAL,
                "환전 신청 반려",
                String.format("%,dP 환전 신청이 반려되었습니다. (사유: %s) 차감됐던 포인트는 환전 가능 포인트로 복원되었습니다.", amt, reason),
                null, null);
    }

    // ---------- 조회/읽음 ----------

    /** 내 알림 목록 (최신순 100건) */
    public List<Notification> getList(long usrSn) {
        return notificationMapper.selectListByUser(usrSn);
    }

    /** 미읽음 개수 (헤더 배지용) */
    public int getUnreadCount(long usrSn) {
        return notificationMapper.countUnread(usrSn);
    }

    /** 개별 읽음 처리 — SQL의 usrSn 가드로 본인 알림만 처리된다 */
    @Transactional
    public void markRead(long ntfSn, long usrSn) {
        notificationMapper.markRead(ntfSn, usrSn);
    }

    /** 전체 읽음 처리 */
    @Transactional
    public void markAllRead(long usrSn) {
        notificationMapper.markAllRead(usrSn);
    }

    // ---------- 알림 수신 설정 (F-COM-012) ----------
    // 도메인 단위 UserNotificationSetting(경매/거래/서비스 3종 고정컬럼)은 2026-07-24부터 설정
    // 화면·저장 API에서는 더 이상 쓰지 않는다(위 이벤트 단위로 교체). 다만 테이블·컬럼과
    // emailEligible()은 그대로 남겨둔다 — 아직 이벤트 목록에 없는 "분쟁 접수/판정"
    // (notifyDisputeReceived/Resolved, TRADE 도메인) 이메일 게이팅이 계속 이 값을 쓰기 때문.
    // 즉 회원이 UI로 이 값을 더 바꿀 방법은 없어졌고, 마지막 저장값(또는 기본 Y)으로 고정된다 —
    // 분쟁 이메일도 세분화하려면 이벤트 목록에 추가하고 이 경로를 걷어내면 된다.
}
