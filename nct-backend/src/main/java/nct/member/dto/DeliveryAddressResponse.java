package nct.member.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeliveryAddressResponse {

    private Long deliveryAddressId;
    private String name;
    private String zip;
    private String address;
    private String addressDetail;
    private boolean defaultAddress;
}
