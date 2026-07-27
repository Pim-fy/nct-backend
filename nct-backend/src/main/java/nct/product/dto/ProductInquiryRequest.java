package nct.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ProductInquiryRequest {

    @NotBlank
    @Size(max = 500)
    private String cn;
}
