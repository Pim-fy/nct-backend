package nct.servicerequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SvcReqCommentRequest {

    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = 30, message = "제목은 30자 이내로 입력해주세요.")
    private String ttl;

    @Size(max = 100, message = "내용은 100자 이내로 입력해주세요.")
    private String cn;
}
