package nct.product.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.product.domain.Product;
import nct.product.dto.ProductResponse;
import nct.product.dto.ProductSummaryResponse;

@Mapper
public interface ProductMapper {

    void saveProduct(Product product);

    Optional<ProductResponse> findProductById(@Param("prdSn") Long prdSn);

    /** 담당자 7 · F-OPS-003: 숨김 상품도 관리자 상세에서 조회합니다. */
    Optional<ProductResponse> findProductForAdminById(@Param("prdSn") Long prdSn);

    List<ProductResponse> findMyProducts(@Param("usrSn") Long usrSn, @Param("filterType") String filterType);

    /** 내 판매 목록 필터 탭 개수 — findMyProducts와 동일한 WHERE 조건을 CASE WHEN 집계로 한 번에 계산 */
    ProductSummaryResponse countMyProductsSummary(@Param("usrSn") Long usrSn);

    Optional<Product> findProductEntityById(@Param("prdSn") Long prdSn);

    void updateProduct(Product product);

    void deleteProduct(@Param("prdSn") Long prdSn, @Param("usrSn") Long usrSn);

    void incrementViewCount(@Param("prdSn") Long prdSn);

    /** 삭제와 구분해 공개 노출 여부만 낙관적으로 전환합니다. */
    int updateProductVisibility(
            @Param("prdSn") Long prdSn,
            @Param("expectedUseYn") String expectedUseYn,
            @Param("useYn") String useYn,
            @Param("actor") String actor);

    /** 활성 상품(사용중·삭제 아님)인 경우에만 현재 조회수 반환 — 조회수 증가 API의 존재/비활성 판정 겸용 */
    Optional<Long> findActiveViewCount(@Param("prdSn") Long prdSn);

    /** 취소 확정(AUCC0005) 후 cutoff 이전에 승인된 상품 prdSn 목록 — 자동 삭제 스케줄러 전용 (AUCTION은 조회만) */
    List<Long> findExpiredCancelledProductIds(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    /** 취소 확정 후 1일 경과 시 시스템이 직접 소프트 삭제 — 소유자 확인 없이 prdSn만으로 처리 */
    void deleteExpiredCancelledProduct(@Param("prdSn") Long prdSn);

    /** 상품 변경사항 알림 대상 조회 — 이 상품 경매에 입찰한 회원 전원(중복 제거). BID/AUCTION은 조회만(기존 합의된 방식) */
    List<Long> findAuctionBidderIds(@Param("prdSn") Long prdSn);

    /** 삭제 전 가드 — 유찰·취소로 종결되지 않은 진행 중 경매가 있는지 확인. AUCTION은 조회만(기존 합의된 방식) */
    boolean existsBlockingAuction(@Param("prdSn") Long prdSn);

    /** 변경사항(F-AUC-007) 등록 가드용 — 경매 진행 상태 조회. AUCTION은 조회만(기존 합의된 방식) */
    Optional<String> findAuctionStatus(@Param("prdSn") Long prdSn);

    /** 구매자 문의 등록 가드용 — 준비·진행 상태이면서 종료시각 전인 경매인지 확인 */
    boolean isAuctionInquiryAvailable(@Param("prdSn") Long prdSn);
}
