package nct.product.service;

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
import nct.product.domain.ProductComment;
import nct.product.dto.ProductCommentRequest;
import nct.product.dto.ProductCommentResponse;
import nct.product.dto.InquiryReportTarget;
import nct.product.dto.ProductInquiryRequest;
import nct.product.dto.ProductInquiryResponse;
import nct.product.domain.ProductTradeRegion;
import nct.product.dto.ProductTradeRegionItem;
import nct.product.mapper.BannedKeywordMapper;
import nct.product.mapper.ProductCommentMapper;
import nct.product.mapper.ProductImageMapper;
import nct.product.mapper.ProductMapper;
import nct.product.mapper.ProductTradeRegionMapper;
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

    private final ProductMapper productMapper;
    private final ReferenceDataService referenceDataService;
    private final ProductImageMapper productImageMapper;
    private final AuctionService auctionService;
    private final TradeService tradeService;
    private final BannedKeywordMapper bannedKeywordMapper;
    private final ProductCommentMapper productCommentMapper;
    private final ProductTradeRegionMapper productTradeRegionMapper;
    private final NotificationService notificationService;

    @Transactional
    public ProductResponse registerProduct(Long usrSn, ProductRegisterRequest req) {
        if (!referenceDataService.isActiveCode("TRDG03", req.getPrdTrdMethodCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateNoBannedKeyword(req.getPrdNm());
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
                    req.getAucEndDt(),
                    true,
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
        validateNoBannedKeyword(req.getPrdNm());

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
                    req.getAucEndDt(),
                    true,
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

    private void validateNoBannedKeyword(String prdNm) {
        if (prdNm == null) return;
        String lower = prdNm.toLowerCase();
        bannedKeywordMapper.findActiveBannedKeywords().stream()
                .filter(kwd -> lower.contains(kwd.toLowerCase()))
                .findFirst()
                .ifPresent(kwd -> {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                            "'" + kwd + "'은(는) 등록할 수 없는 상품명입니다.");
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

    // 희망 거래지역 저장 — 직거래(TRDC0010)·둘 다 가능(TRDC0020)에서만 프론트가 값을 보내지만,
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

        if (productCommentMapper.findLatestComments(prdSn, Integer.MAX_VALUE).size() >= 3) {
            throw new CustomException(ErrorCode.CONFLICT, "변경 내역은 최대 3개까지 등록할 수 있습니다.");
        }

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

    @Transactional
    public void increaseViewCount(Long prdSn) {
        productMapper.incrementViewCount(prdSn);
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

    /** 취소 확정(AUCC0005) 후 cutoff 이전에 승인된 상품 prdSn 목록 — ProductCancelledAutoDeleteScheduler 전용 */
    @Transactional(readOnly = true)
    public List<Long> findExpiredCancelledProductIds(LocalDateTime cutoff, int limit) {
        return productMapper.findExpiredCancelledProductIds(cutoff, limit);
    }

    /** 취소 확정 1일 경과 상품 자동 삭제 — ProductCancelledAutoDeleteScheduler 전용, 소유자 확인 없이 시스템이 직접 처리 */
    @Transactional
    public void deleteExpiredCancelledProduct(Long prdSn) {
        productMapper.deleteExpiredCancelledProduct(prdSn);
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

        productCommentMapper.findLastInquiryTime(usrSn, prdSn).ifPresent(lastTime -> {
            long elapsed = Duration.between(lastTime, LocalDateTime.now()).toHours();
            if (elapsed < INQUIRY_COOLDOWN_HOURS) {
                LocalDateTime nextAvailable = lastTime.plusHours(INQUIRY_COOLDOWN_HOURS);
                throw new CustomException(ErrorCode.INQUIRY_COOLDOWN,
                        String.format("다음 등록 가능 시각: %02d:%02d",
                                nextAvailable.getHour(), nextAvailable.getMinute()));
            }
        });

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
