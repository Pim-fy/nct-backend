package nct.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ProductCommentRequest {

    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = 200, message = "제목은 200자 이내로 입력해주세요.")
    private String ttl;

    @Size(max = 4000, message = "내용은 4000자 이내로 입력해주세요.")
    private String cn;
}
