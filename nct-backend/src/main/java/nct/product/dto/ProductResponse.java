package nct.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Long prdSn;
    private Long usrSn;
    private Long catSn;
    private String catNm;
    private String prdNm;
    private String prdCn;
    private String prdStatusCd;
    private BigDecimal prdStartAmt;
    private BigDecimal prdIbyAmt;
    private String prdTrdMethodCd;
    private LocalDateTime prdRegDt;
    private LocalDateTime prdUpdtDt;

    // 임시저장 시점에 입력해둔 경매설정 보존값 — 상품 등록 재개 시 폼 복원용
    private BigDecimal prdDraftBidUnit;
    private LocalDateTime prdDraftStartDt;
    private LocalDateTime prdDraftEndDt;
    // 임시저장 시점의 즉시시작 여부(Y/N) — 복원 시 startNow 판단용
    private String prdDraftStartNowYn;
    // 임시저장 시점의 경매정책 동의 여부(Y/N) — 재개 시 등록확인 탭 바로 이동 판단용
    private String prdDraftPolicyAgreedYn;

    // 대표이미지 URL (없으면 null — 화면에서 기본 placeholder 처리). 담당자6, F-AUC-002 이미지 연계
    private String prdImgUrl;

    // 희망 거래지역 — 직거래(TRDC0010)·둘 다 가능(TRDC0020)일 때만 값 있음, 상세 조회 시에만 세팅
    @Setter private List<ProductTradeRegionItem> tradeRegions;

    // 상품 이미지 목록 — 상세 조회 시에만 세팅. 목록 조회(me)에서는 null
    @Setter private List<ProductImageItem> imageList;

    // F-AUC-005 경매 상태 연계 — 옥동민(5) AuctionService 일괄 조회로 세팅
    @Setter private Long aucSn;
    @Setter private String aucStatusCd;
    // F-AUC-005 경매 상태 라벨 연계 — AuctionService 일괄 조회 결과로 세팅
    @Setter private String aucStatusNm;

    // F-AUC-005 거래 상태 연계 — 정민재(4) TradeService 일괄 조회로 세팅
    @Setter private Long tradeSn;
    @Setter private String tradeStatusCd;
    @Setter private String tradeMethodCd;
}
