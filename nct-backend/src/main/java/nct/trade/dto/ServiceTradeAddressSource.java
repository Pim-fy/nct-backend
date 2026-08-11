package nct.trade.dto;

/** 서비스 거래 당사자에게만 제공할 요청서 주소 암호문이다. */
public record ServiceTradeAddressSource(
        String encryptedAddress,
        String encryptedDetailAddress,
        String encryptedZonecode) {
}
