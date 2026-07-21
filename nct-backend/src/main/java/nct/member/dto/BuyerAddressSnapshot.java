package nct.member.dto;

/**
 * F-AUC-024 지원: 택배 거래 생성 시 낙찰자(구매자) 주소 스냅샷 조회 결과.
 * MemberService.getBuyerAddressSnapshot이 세 값 모두 non-blank임을 보장한 뒤에만 생성한다.
 */
public record BuyerAddressSnapshot(String zip, String addr, String daddr) {
}
