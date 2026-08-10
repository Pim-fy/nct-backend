package nct.member.dto;

public record BuyerDeliveryAddressSnapshot(
        Long deliveryAddressId,
        String recipientName,
        String recipientPhone,
        String zip,
        String address,
        String addressDetail) {
}
