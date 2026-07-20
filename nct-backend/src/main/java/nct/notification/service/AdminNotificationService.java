// Claude Code 작성 (BJN, 2026-07-19)
package nct.notification.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import nct.audit.domain.AuditLog;
import nct.audit.service.AuditLogService;
import nct.notification.dto.AdminNotificationItem;
import nct.notification.dto.AdminNotificationSummaryResponse;
import nct.notification.mapper.AdminNotificationMapper;
import nct.point.domain.PointExchangeOrder;
import nct.point.service.PointExchangeService;

/**
 * [알림 - 관리자 알림함 요약 계약] (담당자6, F-COM-004/005, 03_관리자/20_notification.html)
 *
 * 목업은 회원·제공자/신고·거래문제/경매·서비스/환전·시스템 4개 탭에 다른 담당자 도메인의
 * 운영 현황까지 모아 보여준다. 각 도메인이 아직 "몇 건 있다"를 내려주는 계약을 안 만들어놔서,
 * 여기서는 그 도메인 테이블을 직접 읽기 전용으로 집계한다(AdminNotificationMapper).
 *
 * linkPath는 담당자6가 실제로 만든 화면(환전·감사로그)에만 채운다 — 다른 카테고리는
 * 아직 관리자 화면 자체가 없어서, 죽은 링크를 만드느니 정보만 보여주고 링크를 비워둔다.
 */
@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    /** "마감임박" 판정 기준 — 목업의 상품 목록 D-1 표기와 같은 24시간 */
    private static final int AUCTION_ENDING_SOON_HOURS = 24;

    private final AdminNotificationMapper adminNotificationMapper;
    private final PointExchangeService pointExchangeService;
    private final AuditLogService auditLogService;

    public AdminNotificationSummaryResponse getSummary() {
        return AdminNotificationSummaryResponse.builder()
                .userProvider(userProviderItems())
                .report(reportItems())
                .auctionService(auctionServiceItems())
                .exchangeSystem(exchangeSystemItems())
                .build();
    }

    private List<AdminNotificationItem> userProviderItems() {
        List<AdminNotificationItem> items = new ArrayList<>();

        int signups = adminNotificationMapper.countNewSignupsToday();
        int withdrawals = adminNotificationMapper.countWithdrawalsToday();
        items.add(AdminNotificationItem.builder()
                .title("회원 가입·탈퇴")
                .detail(String.format("오늘 신규가입 %d명, 탈퇴 %d명", signups, withdrawals))
                .build());

        int pendingProvider = adminNotificationMapper.countPendingProviderApply();
        if (pendingProvider > 0) {
            items.add(AdminNotificationItem.builder()
                    .title("제공자 심사 대기")
                    .detail(String.format("심사 대기 중인 제공자 신청이 %d건 있습니다", pendingProvider))
                    .build());
        }
        return items;
    }

    private List<AdminNotificationItem> reportItems() {
        List<AdminNotificationItem> items = new ArrayList<>();
        int pendingReports = adminNotificationMapper.countPendingReports();
        if (pendingReports > 0) {
            items.add(AdminNotificationItem.builder()
                    .title("신고 접수 대기")
                    .detail(String.format("접수 대기 중인 신고가 %d건 있습니다", pendingReports))
                    .build());
        }
        return items;
    }

    private List<AdminNotificationItem> auctionServiceItems() {
        List<AdminNotificationItem> items = new ArrayList<>();

        int endingSoon = adminNotificationMapper.countAuctionsEndingSoon(AUCTION_ENDING_SOON_HOURS);
        if (endingSoon > 0) {
            items.add(AdminNotificationItem.builder()
                    .title("마감임박 경매")
                    .detail(String.format("%d시간 이내 종료되는 경매가 %d건 있습니다", AUCTION_ENDING_SOON_HOURS, endingSoon))
                    .build());
        }

        int newServiceRequests = adminNotificationMapper.countNewServiceRequestsToday();
        if (newServiceRequests > 0) {
            items.add(AdminNotificationItem.builder()
                    .title("신규 서비스 요청")
                    .detail(String.format("오늘 등록된 서비스 요청이 %d건 있습니다", newServiceRequests))
                    .build());
        }
        return items;
    }

    private List<AdminNotificationItem> exchangeSystemItems() {
        List<AdminNotificationItem> items = new ArrayList<>();

        // 환전 대기 — 담당자6 소유 데이터라 실제 관리자 처리 API로 연결 가능
        List<PointExchangeOrder> pending = pointExchangeService.getRequestedListForAdmin();
        if (!pending.isEmpty()) {
            long totalAmt = pending.stream().mapToLong(PointExchangeOrder::getPtExcOrdAmt).sum();
            items.add(AdminNotificationItem.builder()
                    .title("환전 대기")
                    .detail(String.format("환전 대기 요청이 %d건, %,d원입니다", pending.size(), totalAmt))
                    .build());
        }

        // 최근 감사로그 1건 — 감사로그 화면으로 바로 이동 가능
        List<AuditLog> recentAudit = auditLogService.search(null, null, LocalDateTime.now().minusDays(1), null, 1);
        if (!recentAudit.isEmpty()) {
            AuditLog latest = recentAudit.get(0);
            items.add(AdminNotificationItem.builder()
                    .title("최근 감사로그")
                    .detail((latest.getAudLogTypeNm() != null ? latest.getAudLogTypeNm() : "조치") + " 기록이 남았습니다: "
                            + latest.getAudLogRsonCn())
                    .linkPath("/admin/audit-logs")
                    .build());
        }
        return items;
    }
}
