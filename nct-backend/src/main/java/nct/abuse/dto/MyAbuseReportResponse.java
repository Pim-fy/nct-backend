package nct.abuse.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MyAbuseReportResponse {

    private Long reportSn;
    private String reportTypeCode;
    private String reportTypeName;
    private String targetName;
    private String title;
    private String content;
    private String statusCode;
    private String statusName;
    private String processReason;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
}
