package nct.auction.port;

import nct.auction.dto.ProductBidInfo;

/**
 * [담당자2(상품) 서비스 계약 - 담당자3이 "소비"하는 인터페이스]
 * - PRODUCT 테이블의 고정 기술 소유자는 담당자2 이다.
 * - 담당자3은 이 인터페이스만 바라보고, 실제 구현체(PRODUCT 테이블 조회)는
 *   담당자2 쪽 패키지(예: nct.product.service 등)에서 이 인터페이스를 구현해 Bean 으로 등록한다.
 * - 아직 담당자2 구현이 없다면, 개발 중에는 테스트용 Fake 구현체를
 *   @Primary 로 등록해서 담당자3 개발이 막히지 않도록 진행할 수 있다.
 */
public interface ProductQueryPort {

    /**
     * 입찰 검증에 필요한 상품 정보(판매자, 즉시구매가)만 조회한다.
     * @param prdSn 상품 일련번호 (AUCTION.PRD_SN)
     * @return 판매자 회원번호와 즉시구매금액
     */
    ProductBidInfo getBidInfo(Long prdSn);
}
