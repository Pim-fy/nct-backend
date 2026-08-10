package nct.member.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAddress {

    private Long deliveryAddressId;
    private Long userId;
    private String name;
    private String zipCiphertext;
    private String addressCiphertext;
    private String addressDetailCiphertext;
    private String defaultYn;
    private String useYn;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
    private String registeredBy;
    private String updatedBy;
}
