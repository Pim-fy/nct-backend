package nct.auction.support;

import java.util.HashMap;
import java.util.Map;

import nct.auction.dto.ProductBidInfo;
import nct.auction.port.ProductQueryPort;

/**
 * [담당자2(상품) 계약의 Fake 구현체]
 *
 * "Mock"과 "Fake"의 차이 (학습 포인트):
 *   - Mock  : Mockito 의 when(...).thenReturn(...) 처럼 "이 메서드가 호출되면 이 값을 뱉어라"
 *             하나만 지정하는 껍데기. 호출 여부(verify)를 검증하는 데 특화되어 있다.
 *   - Fake  : 진짜 로직은 아니지만 "그럴듯하게 동작하는" 간이 구현체.
 *             여기서는 상품번호 -> 상품정보를 저장하는 간단한 Map 기반 저장소로 동작한다.
 *             담당자2의 실제 구현체(PRODUCT 테이블 조회)가 아직 없어도,
 *             여러 상품/여러 시나리오를 자유롭게 등록해가며 API 를 반복 호출해볼 수 있다.
 *
 * 이 클래스는 운영 코드(src/main)가 아니라 테스트 코드(src/test)에만 존재한다.
 * 즉, 실제 배포 산출물에는 포함되지 않는다.
 */
public class FakeProductQueryPort implements ProductQueryPort {

    private final Map<Long, ProductBidInfo> storage = new HashMap<>();

    /** 테스트 준비 단계(given)에서 "이 상품은 이런 정보를 가진다"를 등록한다. */
    public void register(Long prdSn, ProductBidInfo info) {
        storage.put(prdSn, info);
    }

    @Override
    public ProductBidInfo getBidInfo(Long prdSn) {
        ProductBidInfo info = storage.get(prdSn);
        if (info == null) {
            // 실제 담당자2 구현체라면 "상품 없음" 예외를 던지겠지만,
            // Fake 에서는 테스트 설정 실수를 바로 알아챌 수 있도록 명확하게 실패시킨다.
            throw new IllegalStateException("FakeProductQueryPort 에 등록되지 않은 상품번호: " + prdSn);
        }
        return info;
    }
}
