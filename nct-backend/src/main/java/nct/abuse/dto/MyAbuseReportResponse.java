package nct.abuse.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
    @JsonIgnore
    private String referenceTypeCode;
    @JsonIgnore
    private Long referenceSn;
    private String title;
    private String content;
    private String statusCode;
    private String statusName;
    private String processReason;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
    private List<AbuseReportFileResponse> files;
}
