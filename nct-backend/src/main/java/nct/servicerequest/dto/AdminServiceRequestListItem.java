package nct.servicerequest.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7: 관리자 서비스 요청 목록에 노출하는 서비스요청 소유 데이터다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminServiceRequestListItem {
    private Long serviceRequestId;
    private String title;
    private Long categoryId;
    private String categoryName;
    private Long requesterUserId;
    private String requesterName;
    private Long budgetAmount;
    private String statusCode;
    private String statusName;
    private String useYn;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
}
