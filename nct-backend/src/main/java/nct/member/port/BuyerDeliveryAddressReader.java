package nct.member.port;

import nct.member.dto.BuyerDeliveryAddressSnapshot;

public interface BuyerDeliveryAddressReader {

    /** 입찰·즉시구매 시 현재 사용 가능한 본인 배송지를 검증합니다. */
    BuyerDeliveryAddressSnapshot getOwnedActiveAddressSnapshot(
            Long buyerUserId,
            Long deliveryAddressId);

    /** 낙찰 거래 생성 시 소프트 삭제된 선택 배송지도 스냅샷으로 복원합니다. */
    BuyerDeliveryAddressSnapshot getOwnedAddressSnapshotForTrade(
            Long buyerUserId,
            Long deliveryAddressId);
}
