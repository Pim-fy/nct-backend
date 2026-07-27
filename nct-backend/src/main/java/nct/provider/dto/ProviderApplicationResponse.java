package nct.provider.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 담당자 7 · 신청자/관리자 화면이 함께 사용하는 제공자 카테고리별 심사 조회 결과입니다. */
@Getter @Setter
public class ProviderApplicationResponse {
    private Long applicationSn;
    private Long userSn;
    private String userName;
    private Long categorySn;
    private String categoryName;
    private String statusCode;
    private String statusName;
    private String rejectReason;
    private String applicationTypeCode;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private List<ProviderApplicationFileResponse> files = List.of();
}
