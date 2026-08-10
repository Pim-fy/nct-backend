package nct.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeliveryAddressRequest {

    @NotBlank(message = "배송지명은 필수입니다.")
    @Size(max = 50, message = "배송지명은 50자를 초과할 수 없습니다.")
    private String name;

    @NotBlank(message = "우편번호는 필수입니다.")
    @Pattern(regexp = "^\\d{5}$", message = "우편번호는 5자리 숫자여야 합니다.")
    private String zip;

    @NotBlank(message = "주소는 필수입니다.")
    @Size(max = 200, message = "주소는 200자를 초과할 수 없습니다.")
    private String address;

    @Size(max = 200, message = "상세주소는 200자를 초과할 수 없습니다.")
    private String addressDetail;

    private boolean defaultAddress;
}
