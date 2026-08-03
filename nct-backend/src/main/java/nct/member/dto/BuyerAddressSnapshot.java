package nct.member.dto;

/**
 * F-AUC-024 지원: 택배 거래 생성 시 낙찰자(구매자) 배송정보 스냅샷 조회 결과.
 * MemberService.getBuyerAddressSnapshot이 우편번호·기본주소를 보장하며, 상세주소·연락처는 빈 값일 수 있다.
 */
public record BuyerAddressSnapshot(
        String recipientName,
        String recipientPhone,
        String zip,
        String addr,
        String daddr) {
}
