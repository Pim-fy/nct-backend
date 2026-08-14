package nct.product.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import nct.auction.dto.AuctionStatusSummaryResponse;
import nct.auction.service.AuctionService;
import nct.common.domain.RefType;
import nct.global.dto.PagedResponse;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.notification.domain.NotificationDomain;
import nct.notification.domain.NotificationType;
import nct.notification.service.NotificationService;
import nct.ops.reference.service.ReferenceDataService;
import nct.product.domain.Product;
import nct.product.domain.ProductImage;
import nct.product.dto.ProductRegisterRequest;
import nct.product.dto.ProductResponse;
import nct.product.dto.ProductSummaryResponse;
import nct.product.domain.ProductComment;
import nct.product.dto.ProductCommentRequest;
import nct.product.dto.ProductCommentResponse;
import nct.product.dto.InquiryReportTarget;
import nct.product.dto.ProductInquiryRequest;
import nct.product.dto.ProductInquiryResponse;
import nct.product.domain.ProductTradeRegion;
import nct.product.dto.ProductTradeRegionItem;
import nct.product.dto.ProductViewResponse;
import nct.product.mapper.BannedKeywordMapper;
import nct.product.mapper.ProductCommentMapper;
import nct.product.mapper.ProductImageMapper;
import nct.product.mapper.ProductMapper;
import nct.product.mapper.ProductTradeRegionMapper;
import nct.product.mapper.ProductViewLogMapper;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.service.TradeService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    // 판매자 답변 등록 후 수정 가능한 시간(분) — F-AUC-012
    private static final long REPLY_EDIT_WINDOW_MINUTES = 10;
    // 구매자 문의 등록 쿨타임(시간) — F-AUC-012
    private static final long INQUIRY_COOLDOWN_HOURS = 6;
    // 클라이언트가 직접 지정할 수 있는 상품 상태 — 종료(PRDC0003)·삭제(PRDC0004)는 서버 내부 전이만 허용
    private static final Set<String> CLIENT_ALLOWED_STATUS_CD = Set.of("PRDC0001", "PRDC0002");

    private void validateClientStatusCd(String statusCd) {
        if (!CLIENT_ALLOWED_STATUS_CD.contains(statusCd)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "허용되지 않는 상품 상태값입니다.");
        }
    }

    // 즉시구매가는 입력하지 않으면 즉시구매 미사용이라 그대로 두고, 입력했다면 시작가보다 높아야 한다(정책 안내와 동일 기준).
    private void validateInstantBuyAboveStartAmt(BigDecimal prdStartAmt, BigDecimal prdIbyAmt) {
        if (prdIbyAmt != null && prdStartAmt != null && prdIbyAmt.compareTo(prdStartAmt) <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "즉시구매가는 시작가보다 높아야 합니다.");
        }
    }

    // 시작가·즉시구매가 상한 — 100,000,000P (사용자 확정, 260810)
    private static final BigDecimal MAX_PRICE_AMT = BigDecimal.valueOf(100_000_000);

    private void validatePriceUnderMax(BigDecimal prdStartAmt, BigDecimal prdIbyAmt) {
        if (prdStartAmt != null && prdStartAmt.compareTo(MAX_PRICE_AMT) > 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "시작가는 " + MAX_PRICE_AMT.toPlainString() + "P 이하로 입력해 주세요.");
        }
        if (prdIbyAmt != null && prdIbyAmt.compareTo(MAX_PRICE_AMT) > 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "즉시구매가는 " + MAX_PRICE_AMT.toPlainString() + "P 이하로 입력해 주세요.");
        }
    }

    private final ProductMapper productMapper;
    private final ReferenceDataService referenceDataService;
    private final ProductImageMapper productImageMapper;
    private final AuctionService auctionService;
    private final TradeService tradeService;
    private final BannedKeywordMapper bannedKeywordMapper;
    private final ProductCommentMapper productCommentMapper;
    private final ProductTradeRegionMapper productTradeRegionMapper;
    private final ProductViewLogMapper productViewLogMapper;
    private final NotificationService notificationService;

    @Transactional
    public ProductResponse registerProduct(Long usrSn, ProductRegisterRequest req) {
        if (!referenceDataService.isActiveCode("TRDG03", req.getPrdTrdMethodCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateNoBannedKeyword(req.getPrdNm(), req.getPrdCn());
        validateInstantBuyAboveStartAmt(req.getPrdStartAmt(), req.getPrdIbyAmt());
        validatePriceUnderMax(req.getPrdStartAmt(), req.getPrdIbyAmt());
        String statusCd = (req.getPrdStatusCd() != null) ? req.getPrdStatusCd() : "PRDC0002";
        validateClientStatusCd(statusCd);
        boolean isDraft = "PRDC0001".equals(statusCd);
        Product product = Product.builder()
                .usrSn(usrSn)
                .catSn(req.getCatSn())
                .prdNm(req.getPrdNm())
                .prdCn(req.getPrdCn())
                .prdStatusCd(statusCd)
                .prdStartAmt(req.getPrdStartAmt())
                .prdIbyAmt(req.getPrdIbyAmt())
                .prdTrdMethodCd(req.getPrdTrdMethodCd())
                .prdRegId(String.valueOf(usrSn))
                .prdUpdtId(String.valueOf(usrSn))
                // 임시저장 시점에는 아직 AUCTION row가 없어 입찰단위·경매기간을 PRODUCT에 보존해뒀다가
                // 재개 시 폼 복원용으로 돌려준다. 공개 등록 전환 시에는 비워 정식 AUCTION 값만 남긴다.
                .prdDraftBidUnit(isDraft ? req.getBidUnit() : null)
                .prdDraftStartDt(isDraft ? req.getAucStartDt() : null)
                .prdDraftEndDt(isDraft ? req.getAucEndDt() : null)
                .prdDraftStartNowYn(isDraft ? (Boolean.TRUE.equals(req.getStartNow()) ? "Y" : "N") : null)
                .prdDraftPolicyAgreedYn(isDraft ? draftPolicyAgreedYn(req) : null)
                .build();

        productMapper.saveProduct(product);
        saveImages(product.getPrdSn(), req.getFlSnList());
        saveTradeRegions(product.getPrdSn(), req.getTradeRegions());

        if ("PRDC0002".equals(statusCd) && req.getAucEndDt() != null) {
            auctionService.createAuctionForProduct(
                    product.getPrdSn(),
                    req.getPrdStartAmt(),
                    req.getBidUnit(),
                    req.getAucStartDt(),
                    req.getAucEndDt(),
                    usrSn);
        }

        return productMapper.findProductById(product.getPrdSn())
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Transactional
    public ProductResponse updateProduct(Long prdSn, Long usrSn, ProductRegisterRequest req) {
        Product existing = productMapper.findProductEntityById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!existing.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (!"PRDC0001".equals(existing.getPrdStatusCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "임시저장 상태의 상품만 수정할 수 있습니다.");
        }
        if (!referenceDataService.isActiveCode("TRDG03", req.getPrdTrdMethodCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateNoBannedKeyword(req.getPrdNm(), req.getPrdCn());
        validateInstantBuyAboveStartAmt(req.getPrdStartAmt(), req.getPrdIbyAmt());
        validatePriceUnderMax(req.getPrdStartAmt(), req.getPrdIbyAmt());

        String statusCd = (req.getPrdStatusCd() != null) ? req.getPrdStatusCd() : "PRDC0002";
        validateClientStatusCd(statusCd);
        boolean isDraft = "PRDC0001".equals(statusCd);
        Product updated = Product.builder()
                .prdSn(prdSn)
                .usrSn(usrSn)
                .catSn(req.getCatSn())
                .prdNm(req.getPrdNm())
                .prdCn(req.getPrdCn())
                .prdStatusCd(statusCd)
                .prdStartAmt(req.getPrdStartAmt())
                .prdIbyAmt(req.getPrdIbyAmt())
                .prdTrdMethodCd(req.getPrdTrdMethodCd())
                .prdUpdtId(String.valueOf(usrSn))
                .prdDraftBidUnit(isDraft ? req.getBidUnit() : null)
                .prdDraftStartDt(isDraft ? req.getAucStartDt() : null)
                .prdDraftEndDt(isDraft ? req.getAucEndDt() : null)
                .prdDraftStartNowYn(isDraft ? (Boolean.TRUE.equals(req.getStartNow()) ? "Y" : "N") : null)
                .prdDraftPolicyAgreedYn(isDraft ? draftPolicyAgreedYn(req) : null)
                .build();

        productMapper.updateProduct(updated);

        if (req.getFlSnList() != null && !req.getFlSnList().isEmpty()) {
            productImageMapper.deleteByPrdSn(prdSn);
            saveImages(prdSn, req.getFlSnList());
        }

        productTradeRegionMapper.deleteByPrdSn(prdSn);
        saveTradeRegions(prdSn, req.getTradeRegions());

        if ("PRDC0002".equals(statusCd) && req.getAucEndDt() != null) {
            auctionService.createAuctionForProduct(
                    prdSn,
                    req.getPrdStartAmt(),
                    req.getBidUnit(),
                    req.getAucStartDt(),
                    req.getAucEndDt(),
                    usrSn);
        }

        return productMapper.findProductById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Transactional(readOnly = true)
    public List<String> getBannedKeywords() {
        return bannedKeywordMapper.findActiveBannedKeywords();
    }

    private String draftPolicyAgreedYn(ProductRegisterRequest req) {
        return Boolean.TRUE.equals(req.getPolicyAgreed()) ? "Y" : "N";
    }

    // 상품 변경사항(F-AUC-007) 등록 시 이 상품에 입찰한 회원 전원에게 알림 — 알림 발행은
    // NotificationService.notify()로 직접 호출(담당자6 계약 — "다른 도메인은 notify()만 호출")
    private void notifyBiddersOfProductChange(Product product, String changeTitle) {
        List<Long> bidderIds = productMapper.findAuctionBidderIds(product.getPrdSn());
        for (Long bidderUsrSn : bidderIds) {
            notificationService.notify(
                    bidderUsrSn,
                    NotificationType.AUCTION,
                    NotificationDomain.AUCTION,
                    "입찰한 상품에 변경사항이 추가되었습니다",
                    product.getPrdNm() + " - " + changeTitle,
                    RefType.PRODUCT,
                    product.getPrdSn());
        }
    }

    // 상품명뿐 아니라 본문(설명)도 같은 금지 키워드 목록으로 검사한다(F-AUC-004) —
    // 상품명만 막으면 제목엔 안 넣고 본문에만 넣는 방식으로 우회할 수 있어서 같이 막는다.
    private void validateNoBannedKeyword(String prdNm, String prdCn) {
        List<String> activeKeywords = bannedKeywordMapper.findActiveBannedKeywords();
        checkBannedKeyword(prdNm, "상품명", activeKeywords);
        checkBannedKeyword(prdCn, "상품 설명", activeKeywords);
    }

    private void checkBannedKeyword(String text, String fieldLabel, List<String> keywords) {
        if (text == null) return;
        String lower = text.toLowerCase();
        keywords.stream()
                .filter(kwd -> lower.contains(kwd.toLowerCase()))
                .findFirst()
                .ifPresent(kwd -> {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                            "'" + kwd + "'은(는) 등록할 수 없는 " + fieldLabel + "입니다.");
                });
    }

    // 업로드된 파일 id 목록을 PRODUCT_IMAGE로 연결 — 목록 순서상 첫 번째가 대표(F-AUC-002)
    private void saveImages(Long prdSn, List<Long> flSnList) {
        if (flSnList == null || flSnList.isEmpty()) return;

        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < flSnList.size(); i++) {
            images.add(ProductImage.builder()
                    .prdSn(prdSn)
                    .flSn(flSnList.get(i))
                    .prdImgRprsYn(i == 0 ? "Y" : "N")
                    .prdImgSortNo(i)
                    .build());
        }
        productImageMapper.insertAll(images);
    }

    // 희망 거래지역 저장 — 직거래(TRDC0010)·둘 다 가능(TRDC0015)에서만 프론트가 값을 보내지만,
    // 어떤 거래방식이든 보낸 값 그대로 저장한다(검증은 요청 시점 @Size(max=5)로 충분)
    private void saveTradeRegions(Long prdSn, List<ProductTradeRegionItem> tradeRegions) {
        if (tradeRegions == null || tradeRegions.isEmpty()) return;

        List<ProductTradeRegion> regions = tradeRegions.stream()
                .map(r -> ProductTradeRegion.builder()
                        .prdSn(prdSn)
                        .rgnCd(r.getCode())
                        .rgnNm(r.getName())
                        .build())
                .collect(Collectors.toList());
        productTradeRegionMapper.insertAll(regions);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long prdSn) {
        ProductResponse product = productMapper.findProductById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        product.setImageList(productImageMapper.findImagesByPrdSn(prdSn));
        product.setTradeRegions(productTradeRegionMapper.findByPrdSn(prdSn));
        return product;
    }

    /**
     * 담당자 7 · F-OPS-003: 관리자 경매 운영 화면은 공개 숨김 상품도 조회합니다.
     * 실제 삭제 상태(PRDC0004)는 복구 대상이 아니므로 조회하지 않습니다.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductForAdmin(Long prdSn) {
        if (prdSn == null || prdSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ProductResponse product = productMapper.findProductForAdminById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        product.setImageList(productImageMapper.findImagesByPrdSn(prdSn));
        product.setTradeRegions(productTradeRegionMapper.findByPrdSn(prdSn));
        return product;
    }

    /** 담당자 7 · F-OPS-003: 상품 삭제 없이 공개 노출만 숨김·복구합니다. */
    @Transactional
    public boolean changeVisibilityForAdmin(Long prdSn, boolean visible, Long adminUserSn) {
        if (prdSn == null || prdSn <= 0 || adminUserSn == null || adminUserSn <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ProductResponse product = productMapper.findProductForAdminById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        String currentUseYn = product.getPrdUseYn();
        String nextUseYn = visible ? "Y" : "N";
        if (nextUseYn.equals(currentUseYn)) {
            return false;
        }
        if (productMapper.updateProductVisibility(
                prdSn,
                currentUseYn,
                nextUseYn,
                String.valueOf(adminUserSn)) != 1) {
            throw new CustomException(ErrorCode.CONFLICT, "상품 노출 상태가 이미 변경되었습니다.");
        }
        return true;
    }

    /** 내 판매 목록 필터 탭 개수 — 목록 조회(getMyProducts)와 달리 경매·거래 상태 enrichment 없이 개수만 집계 */
    @Transactional(readOnly = true)
    public ProductSummaryResponse getMyProductsSummary(Long usrSn) {
        return productMapper.countMyProductsSummary(usrSn);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> getMyProducts(Long usrSn, int page, int size, String filterType) {
        PageHelper.startPage(page, size);
        List<ProductResponse> list = productMapper.findMyProducts(usrSn, filterType);
        PagedResponse<ProductResponse> result = PagedResponse.of(new PageInfo<>(list));

        List<Long> prdSns = result.getList().stream()
                .map(ProductResponse::getPrdSn)
                .collect(Collectors.toList());

        Map<Long, AuctionStatusSummaryResponse> statusMap =
                auctionService.getAuctionStatusesByProducts(prdSns).stream()
                        .collect(Collectors.toMap(AuctionStatusSummaryResponse::getPrdSn, s -> s));

        Map<Long, SellerTradeStatusItem> tradeStatusMap =
                tradeService.getTradeStatusesByProducts(prdSns).stream()
                        // 비정상 중복 거래가 있으면 SQL 정렬상 최신 상태를 우선한다.
                        .collect(Collectors.toMap(
                                SellerTradeStatusItem::getPrdSn,
                                status -> status,
                                (existing, ignored) -> existing));

        result.getList().forEach(p -> {
            AuctionStatusSummaryResponse s = statusMap.get(p.getPrdSn());
            if (s != null) {
                p.setAucSn(s.getAucSn());
                p.setAucStatusCd(s.getAucStatusCd());
                p.setAucStatusNm(s.getAucStatusNm());
            }

            SellerTradeStatusItem tradeStatus = tradeStatusMap.get(p.getPrdSn());
            if (tradeStatus != null) {
                p.setTradeSn(tradeStatus.getTradeSn());
                p.setTradeStatusCd(tradeStatus.getTradeStatusCd());
                p.setTradeMethodCd(tradeStatus.getTradeMethodCd());
                p.setTradeAmount(tradeStatus.getTradeAmount());
                p.setTradeCreatedAt(tradeStatus.getCreatedAt());
                p.setTradeCompletedAt(tradeStatus.getCompletedAt());
            }
        });

        return result;
    }

    @Transactional
    public ProductCommentResponse addComment(Long prdSn, Long usrSn, ProductCommentRequest req) {
        Product product = productMapper.findProductEntityById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        // 경매가 진행 중(AUCC0002)일 때만 변경사항 등록 허용 — 유찰·낙찰·취소 등으로 이미 종료된 경매에는
        // 입찰자에게 알릴 이유가 없어 막는다
        String aucStatusCd = productMapper.findAuctionStatus(prdSn).orElse(null);
        if (!"AUCC0002".equals(aucStatusCd)) {
            throw new CustomException(ErrorCode.CONFLICT, "진행 중인 경매에만 변경사항을 등록할 수 있습니다.");
        }

        if (productCommentMapper.findLatestComments(prdSn, Integer.MAX_VALUE).size() >= 3) {
            throw new CustomException(ErrorCode.CONFLICT, "변경 내역은 최대 3개까지 등록할 수 있습니다.");
        }

        List<String> bannedKeywords = bannedKeywordMapper.findActiveBannedKeywords();
        checkBannedKeyword(req.getTtl(), "변경사항 제목", bannedKeywords);
        checkBannedKeyword(req.getCn(), "변경사항 내용", bannedKeywords);

        ProductComment comment = ProductComment.builder()
                .prdSn(prdSn)
                .usrSn(usrSn)
                .prdCmtTypeCd("PRDC0005")
                .prdCmtTtl(req.getTtl())
                .prdCmtCn(req.getCn())
                .prdCmtRegId(String.valueOf(usrSn))
                .prdCmtUpdtId(String.valueOf(usrSn))
                .build();

        productCommentMapper.insertComment(comment);
        notifyBiddersOfProductChange(product, req.getTtl());

        return productCommentMapper.findLatestComments(prdSn, 1).get(0);
    }

    @Transactional(readOnly = true)
    public List<ProductCommentResponse> getComments(Long prdSn) {
        productMapper.findProductById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        return productCommentMapper.findLatestComments(prdSn, Integer.MAX_VALUE);
    }

    /**
     * 상품 조회수 증가 (F-AUC-006) — 옥동민(5) 경매 상세 조회 시 호출.
     * 동일 방문자(로그인=usrSn, 비로그인=익명 쿠키)가 동일 상품을 24시간 내 재조회하면 증가시키지 않는다.
     * 중복 판정과 카운트 증가를 하나의 트랜잭션으로 묶어 판정과 반영 사이 불일치가 없게 한다.
     */
    @Transactional
    public ProductViewResponse increaseViewCount(Long prdSn, Long usrSn, String anonVisitorKey) {
        long viewCount = productMapper.findActiveViewCount(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        String visitorKey = usrSn != null ? String.valueOf(usrSn) : anonVisitorKey;
        LocalDateTime since = LocalDateTime.now().minusHours(24);

        if (productViewLogMapper.existsRecentView(prdSn, visitorKey, since)) {
            return new ProductViewResponse(false, viewCount);
        }

        productViewLogMapper.insert(prdSn, visitorKey);
        productMapper.incrementViewCount(prdSn);
        return new ProductViewResponse(true, viewCount + 1);
    }

    @Transactional
    public void deleteProduct(Long prdSn, Long usrSn) {
        Product product = productMapper.findProductEntityById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        // 유찰(AUCC0004)·취소(AUCC0005)로 이미 종결된 경매만 삭제 허용 — 그 외(준비·진행·종료·취소요청)
        // 상태에서 삭제하면 관리자 승인이 필수인 경매취소 절차를 우회해 입찰 홀딩이 방치된다.
        if (productMapper.existsBlockingAuction(prdSn)) {
            throw new CustomException(ErrorCode.CONFLICT,
                    "진행 중인 경매가 있는 상품은 삭제할 수 없습니다. 경매 취소 요청을 이용해 주세요.");
        }

        productMapper.deleteProduct(prdSn, usrSn);
    }

    /** 신고 검증용 문의 단건 조회 — 타 도메인 공개 계약 (F-AUC-013) */
    @Transactional(readOnly = true)
    public InquiryReportTarget getInquiryReportTarget(Long prdCmtSn) {
        return productCommentMapper.findInquiryReportTarget(prdCmtSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /** 구매자 문의 등록 (F-AUC-012) */
    @Transactional
    public ProductInquiryResponse addInquiry(Long prdSn, Long usrSn, ProductInquiryRequest req) {
        ProductResponse product = productMapper.findProductById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!productMapper.isAuctionInquiryAvailable(prdSn)) {
            throw new CustomException(ErrorCode.INQUIRY_NOT_AVAILABLE);
        }

        productCommentMapper.findLastInquiryTime(usrSn, prdSn).ifPresent(lastTime -> {
            long elapsed = Duration.between(lastTime, LocalDateTime.now()).toHours();
            if (elapsed < INQUIRY_COOLDOWN_HOURS) {
                LocalDateTime nextAvailable = lastTime.plusHours(INQUIRY_COOLDOWN_HOURS);
                throw new CustomException(ErrorCode.INQUIRY_COOLDOWN,
                        String.format("다음 등록 가능 시각: %02d:%02d",
                                nextAvailable.getHour(), nextAvailable.getMinute()));
            }
        });

        validateInquiryContent(req.getCn());

        ProductComment inquiry = ProductComment.builder()
                .prdSn(prdSn)
                .usrSn(usrSn)
                .prdCmtCn(req.getCn())
                .prdCmtRegId(String.valueOf(usrSn))
                .prdCmtUpdtId(String.valueOf(usrSn))
                .build();

        productCommentMapper.insertInquiry(inquiry);
        notificationService.notifyInquiryReceived(product.getUsrSn(), inquiry.getPrdCmtSn());

        return productCommentMapper.findInquiries(prdSn).stream()
                .filter(r -> r.getPrdCmtSn().equals(inquiry.getPrdCmtSn()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /** 구매자 문의 수정 — 본인만, 답변 전까지만 가능 (F-AUC-012) */
    @Transactional
    public ProductInquiryResponse updateInquiry(Long prdSn, Long inquirySn, Long usrSn, ProductInquiryRequest req) {
        ProductComment inquiry = productCommentMapper.findInquiryById(inquirySn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!inquiry.getPrdSn().equals(prdSn)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!inquiry.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        validateInquiryContent(req.getCn());

        int updated = productCommentMapper.updateInquiry(ProductComment.builder()
                .prdCmtSn(inquirySn)
                .prdCmtCn(req.getCn())
                .prdCmtUpdtId(String.valueOf(usrSn))
                .build());

        if (updated == 0) {
            throw new CustomException(ErrorCode.CONFLICT, "이미 답변이 등록된 문의는 수정할 수 없습니다.");
        }

        return productCommentMapper.findInquiries(prdSn).stream()
                .filter(r -> r.getPrdCmtSn().equals(inquirySn))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /** 백종남(6) 알림 클릭 이동용 — prdCmtSn → prdSn 변환 */
    @Transactional(readOnly = true)
    public Long getProductSnByInquirySn(Long prdCmtSn) {
        return productCommentMapper.findProductSnByInquirySn(prdCmtSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /** 구매자 문의 목록 조회 (F-AUC-012) */
    @Transactional(readOnly = true)
    public List<ProductInquiryResponse> getInquiries(Long prdSn) {
        productMapper.findProductById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        return productCommentMapper.findInquiries(prdSn);
    }

    private void validateInquiryContent(String content) {
        checkBannedKeyword(
                content,
                "문의 내용",
                bannedKeywordMapper.findActiveBannedKeywords());
    }

    /** 판매자 답변 등록 (F-AUC-012) */
    @Transactional
    public ProductInquiryResponse addReply(Long prdSn, Long inquirySn, Long usrSn, ProductInquiryRequest req) {
        Product product = productMapper.findProductEntityById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        ProductComment inquiry = productCommentMapper.findInquiryById(inquirySn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!inquiry.getPrdSn().equals(prdSn)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (productCommentMapper.findReplyByParentSn(inquirySn).isPresent()) {
            throw new CustomException(ErrorCode.CONFLICT, "이미 답변이 등록된 문의입니다.");
        }

        checkBannedKeyword(req.getCn(), "답변 내용", bannedKeywordMapper.findActiveBannedKeywords());

        ProductComment reply = ProductComment.builder()
                .prdSn(prdSn)
                .usrSn(usrSn)
                .prdCmtParentSn(inquirySn)
                .prdCmtCn(req.getCn())
                .prdCmtRegId(String.valueOf(usrSn))
                .prdCmtUpdtId(String.valueOf(usrSn))
                .build();

        productCommentMapper.insertReply(reply);
        notificationService.notifyInquiryReplied(inquiry.getUsrSn(), reply.getPrdCmtSn());

        return productCommentMapper.findInquiries(prdSn).stream()
                .filter(r -> r.getPrdCmtSn().equals(reply.getPrdCmtSn()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /** 판매자 답변 수정 — 등록 후 10분 이내에만 가능 (F-AUC-012) */
    @Transactional
    public ProductInquiryResponse updateReply(Long prdSn, Long inquirySn, Long usrSn, ProductInquiryRequest req) {
        Product product = productMapper.findProductEntityById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        ProductComment reply = productCommentMapper.findReplyByParentSn(inquirySn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!reply.getPrdSn().equals(prdSn)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (Duration.between(reply.getPrdCmtRegDt(), LocalDateTime.now()).toMinutes() >= REPLY_EDIT_WINDOW_MINUTES) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "답변 등록 후 10분이 지나 수정할 수 없습니다.");
        }

        checkBannedKeyword(req.getCn(), "답변 내용", bannedKeywordMapper.findActiveBannedKeywords());

        ProductComment updated = ProductComment.builder()
                .prdCmtSn(reply.getPrdCmtSn())
                .prdCmtCn(req.getCn())
                .prdCmtUpdtId(String.valueOf(usrSn))
                .build();

        productCommentMapper.updateReply(updated);

        return productCommentMapper.findInquiries(prdSn).stream()
                .filter(r -> r.getPrdCmtSn().equals(reply.getPrdCmtSn()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
