package nct.auction.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당자 7 · F-COM-018: 신고 이력에서 경매 번호 대신 검증된 상품 제목을 표시하기 위한 읽기 전용 값입니다. */
@Getter
@Setter
@NoArgsConstructor
public class AuctionReferenceTitle {

    private Long auctionId;
    private String title;
}
