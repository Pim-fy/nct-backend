package nct.trade.dto;

import lombok.Data;

/** 화면이 권한 있는 다운로드 URL을 요청하는 데 필요한 배송 인증사진 식별자다. */
@Data
public class TradeDeliveryProofFile {

    private Long fileId;
    private Integer sortOrder;
}
