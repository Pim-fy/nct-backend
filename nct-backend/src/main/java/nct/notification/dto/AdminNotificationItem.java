// Claude Code 작성 (BJN, 2026-07-19)
package nct.notification.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * [알림 - 관리자 알림함 카드 1건] (담당자6, 03_관리자/20_notification.html)
 * - linkPath는 실제로 존재하는 관리자 화면일 때만 채운다. 대상 화면이 아직 없는 카테고리는
 *   null로 둬서 프론트가 죽은 링크를 만들지 않게 한다.
 */
@Getter
@Builder
public class AdminNotificationItem {

    private String title;
    private String detail;
    private String linkPath;
}
