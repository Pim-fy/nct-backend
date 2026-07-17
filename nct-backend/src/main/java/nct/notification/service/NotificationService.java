package nct.notification.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.common.domain.RefType;
import nct.notification.domain.Notification;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.domain.UserNotificationSetting;
import nct.notification.mapper.NotificationMapper;
import nct.notification.mapper.UserNotificationSettingMapper;

/**
 * [알림 - 서비스 계약] (담당자6 백종남, F-UX-064/065)
 *
 * 다른 도메인은 이벤트 발생 시 notify(...)만 호출한다 — NOTIFICATION 테이블 직접 INSERT 금지.
 * 호출하는 쪽 트랜잭션 안에서 부르면 같은 트랜잭션으로 묶인다 (본 이벤트 실패 시 알림도 함께 롤백).
 *
 * 이메일 발송 채널은 DEC-064 확정 전이므로 모든 알림을 미대상(NTFC0006)으로 기록한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 이메일발송상태(NTFG02) 미대상 — 이메일 채널 확정 전 고정값 */
    private static final String EMAIL_STATUS_NONE = "NTFC0006";

    private final NotificationMapper notificationMapper;
    private final UserNotificationSettingMapper settingMapper;

    /**
     * 범용 알림 생성 (모든 알림의 단일 진입점).
     *
     * @param refType 알림을 발생시킨 참조 유형 (없으면 null 허용)
     * @param refSn   참조 일련번호
     */
    @Transactional
    public void notify(long usrSn, NotificationType type, NotificationDomain domain,
                       String title, String content, RefType refType, Long refSn) {
        Notification n = new Notification();
        n.setUsrSn(usrSn);
        n.setNtfTypeCd(type.getCode());
        n.setNtfDomainCd(domain.getCode());
        n.setNtfTtl(title);
        n.setNtfCn(content);
        n.setNtfRefTypeCd(refType != null ? refType.getCode() : null);
        n.setNtfRefSn(refSn);
        n.setNtfEmailStatusCd(EMAIL_STATUS_NONE);
        notificationMapper.insert(n);
    }

    /** 포인트 홀딩 반환 알림 (업무분장: 입찰·낙찰·반환 알림) — PointService.releaseHold가 호출 */
    public void notifyPointRelease(long usrSn, long amt, RefType refType, long refSn, String reason) {
        notify(usrSn, NotificationType.BID, NotificationDomain.AUCTION,
                "포인트 반환",
                String.format("%,dP가 사용 가능 포인트로 반환되었습니다. (%s)", amt, reason),
                refType, refSn);
    }

    /** 정산 이벤트 알림 — SettlementService가 호출 */
    public void notifySettlement(long usrSn, String title, String content, long trdSn) {
        notify(usrSn, NotificationType.TRADE, NotificationDomain.TRADE,
                title, content, RefType.TRADE, trdSn);
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

    /** 환전 지급 완료 알림 — 관리자가 실제 이체를 마치고 완료 처리하면 나간다 */
    public void notifyExchangeComplete(long usrSn, long amt) {
        notify(usrSn, NotificationType.OPS, NotificationDomain.OPS,
                "환전 지급 완료",
                String.format("%,dP 환전 지급이 완료되었습니다. 등록하신 계좌를 확인해 주세요.", amt),
                null, null);
    }

    /** 환전 반려 알림 — 차감했던 포인트가 복원됐음을 사유와 함께 안내한다 */
    public void notifyExchangeReject(long usrSn, long amt, String reason) {
        notify(usrSn, NotificationType.OPS, NotificationDomain.OPS,
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
