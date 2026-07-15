package nct.auction.port.stub;

import org.springframework.stereotype.Component;

import nct.auction.dto.ProductBidInfo;
import nct.auction.port.ProductQueryPort;

import lombok.extern.slf4j.Slf4j;

/**
 * ================================================================================
 *  [임시 구현체 - TODO: 담당자2 실제 구현체로 교체 필요]
 * ================================================================================
 * - PRODUCT 테이블의 고정 기술 소유자는 담당자2 이다. 이 클래스는 그 실제 구현체가
 *   아직 만들어지지 않아 애플리케이션이 아예 기동조차 안 되는 상황(빈 부재)을 막기 위한
 *   "최소 동작 대역"일 뿐, PRODUCT 테이블을 실제로 조회하지 않는다.
 * - 담당자2가 실제 ProductQueryPort 구현체(PRODUCT 테이블 조회)를 만들어 Bean 으로
 *   등록하면, Spring 컨테이너에 같은 타입의 Bean 이 2개가 되어 기동 시 충돌이 난다.
 *   그때 이 클래스는 반드시 삭제해야 한다 (또는 이 파일 자체를 지운다).
 * - 절대 실제 운영 데이터로 착각해서 이 값을 신뢰하면 안 되므로, 호출될 때마다
 *   경고 로그를 남겨 "아직 스텁이 살아있다"는 사실을 눈에 띄게 알린다.
 * ================================================================================
 */
@Slf4j
@Component
public class StubProductQueryPort implements ProductQueryPort {

    @Override
    public ProductBidInfo getBidInfo(Long prdSn) {
        log.warn("[STUB] StubProductQueryPort.getBidInfo() 호출됨 (prdSn={}) - " +
                 "담당자2의 실제 PRODUCT 조회 구현체로 반드시 교체해야 합니다.", prdSn);

        return ProductBidInfo.builder()
                // -1L: 실제 회원번호 체계(AUTO_INCREMENT, 1부터 시작)와 절대 겹치지 않는 값을
                //      의도적으로 사용해서, 자기입찰 차단 검증이 스텁 때문에 잘못 통과되지 않게 한다.
                .sellerUsrSn(-1L)
                // null: 즉시구매가 제한 없음으로 처리되어 4번 규칙(즉시구매가 미만 검증)을 건너뛴다.
                .buyNowAmt(null)
                .build();
    }
}
