package nct.notification.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.notification.domain.Notification;
import nct.notification.domain.NotificationAudience;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationEmailStatus;
import nct.notification.domain.NotificationType;
import nct.notification.domain.UserNotificationSetting;
import nct.notification.dto.NotificationResponse;
import nct.notification.mapper.NotificationMapper;
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
    private final SystemSettingAdminMapper systemSettingMapper;
    private final NotificationMailSender mailSender;
    private final NotificationEventPublisher eventPublisher;

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
        n.setNtfEmailStatusCd(NotificationEmailStatus.NONE.getCode());
        notificationMapper.insert(n);
        eventPublisher.publishAfterCommit(usrSn, NotificationResponse.from(n));
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
            n.setNtfEmailStatusCd(NotificationEmailStatus.NONE.getCode());
            notificationMapper.insert(n);
            eventPublisher.publishAfterCommit(usrSn, NotificationResponse.from(n));
            return;
        }

        // 대상이면: 대기 상태로 먼저 기록 → 발송 시도 → 결과(성공/실패)로 갱신.
        // 발송기(mailSender)는 예외를 던지지 않는 계약이라 이 흐름이 본 트랜잭션을 깨뜨릴 수 없다
        n.setNtfEmailStatusCd(NotificationEmailStatus.PENDING.getCode());
        notificationMapper.insert(n);
        eventPublisher.publishAfterCommit(usrSn, NotificationResponse.from(n));

        String email = notificationMapper.selectUserEmail(usrSn);
        boolean sent = email != null
                && mailSender.send(email, "[에누리컷] " + title, content
                        + "\n\n자세한 내용은 에누리컷 알림함에서 확인해 주세요. (본 메일은 발신 전용입니다)");
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

    // ---------- 중요 이벤트 트리거 계약 (F-COM-006) — 거래·분쟁 담당자(4·5)가 호출 ----------

    /** 거래 완료 확인 요청 — 상대방 확인 대기 시작 시 거래 담당자(4)가 호출. 기한 내 미확인 시 자동완료 */
    public void notifyTradeConfirmRequest(long usrSn, long trdSn, int confirmDays) {
        notifyImportant(usrSn, NotificationType.OPS, NotificationDomain.TRADE, NotificationAudience.GENERAL,
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

    /** 포인트 충전 완료 알림 — PointChargeService가 호출 */
    public void notifyCharge(long usrSn, long amt) {
        notify(usrSn, NotificationType.OPS, NotificationDomain.OPS,
                "충전 완료",
                String.format("%,dP가 충전되었습니다.", amt),
                null, null);
    }

    /**
     * 환전 신청 접수 알림 — PointExchangeService가 호출 (F-PAY-012, D-026)
     * 실제 입금은 관리자 수동 처리라 "며칠 내 지급 예정" 안내만 나간다 (자동화 금지 정본 규칙)
     */
    public void notifyExchangeRequest(long usrSn, long amt) {
        notify(usrSn, NotificationType.OPS, NotificationDomain.OPS,
                "환전 신청 접수",
                String.format("%,dP 환전 신청이 접수되었습니다. 등록하신 계좌로 며칠 내 지급될 예정입니다.", amt),
                null, null);
    }

    /** 환전 지급 완료 알림 — 실제 돈이 오간 결과라 이메일 보조 발송 대상 (F-COM-006 트리거) */
    public void notifyExchangeComplete(long usrSn, long amt) {
        notifyImportant(usrSn, NotificationType.OPS, NotificationDomain.OPS, NotificationAudience.GENERAL,
                "환전 지급 완료",
                String.format("%,dP 환전 지급이 완료되었습니다. 등록하신 계좌를 확인해 주세요.", amt),
                null, null);
    }

    /** 포인트 전환 완료 알림 (F-PAY-010) — 본인 지갑 관리 이벤트라 '일반' 구분(기본값)으로 나간다 */
    public void notifyPointConvert(long usrSn, long amt) {
        notify(usrSn, NotificationType.OPS, NotificationDomain.OPS,
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

    /** 환전 반려 알림 — 실제 돈이 오간 결과라 이메일 보조 발송 대상 (F-COM-006 트리거) */
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
    // 저장·조회 계약만 제공한다. notify(...) 발송 시 설정을 반영해 인앱 저장을 건너뛸지는
    // 알림함 소비 계약(전 담당자)에 영향을 주는 변경이라 별도 결정 후 반영 — 임의 적용 금지.

    /** 내 알림 수신 설정 조회 — 저장한 적 없는 회원은 기본값(전 채널 수신)을 돌려준다 */
    public UserNotificationSetting getSetting(long usrSn) {
        UserNotificationSetting setting = settingMapper.selectByUser(usrSn);
        return setting != null ? setting : UserNotificationSetting.defaultOf(usrSn);
    }

    /** 내 알림 수신 설정 저장 — 회원당 1행 업서트(없으면 생성, 있으면 갱신) */
    @Transactional
    public void saveSetting(UserNotificationSetting setting) {
        settingMapper.upsert(setting);
    }
}
