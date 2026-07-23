package nct.product.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import nct.auction.dto.AuctionStatusSummaryResponse;
import nct.auction.service.AuctionService;
import nct.global.dto.PagedResponse;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.reference.service.ReferenceDataService;
import nct.product.domain.Product;
import nct.product.domain.ProductImage;
import nct.product.dto.ProductRegisterRequest;
import nct.product.dto.ProductResponse;
import nct.product.domain.ProductComment;
import nct.product.dto.ProductCommentRequest;
import nct.product.dto.ProductCommentResponse;
import nct.product.mapper.BannedKeywordMapper;
import nct.product.mapper.ProductCommentMapper;
import nct.product.mapper.ProductImageMapper;
import nct.product.mapper.ProductMapper;
import nct.trade.dto.SellerTradeStatusItem;
import nct.trade.service.TradeService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final ReferenceDataService referenceDataService;
    private final ProductImageMapper productImageMapper;
    private final AuctionService auctionService;
    private final TradeService tradeService;
    private final BannedKeywordMapper bannedKeywordMapper;
    private final ProductCommentMapper productCommentMapper;

    @Transactional
    public ProductResponse registerProduct(Long usrSn, ProductRegisterRequest req) {
        if (!referenceDataService.isActiveCode("TRDG03", req.getPrdTrdMethodCd())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateNoBannedKeyword(req.getPrdNm());
        String statusCd = (req.getPrdStatusCd() != null) ? req.getPrdStatusCd() : "PRDC0002";
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
                .build();

        productMapper.saveProduct(product);
        saveImages(product.getPrdSn(), req.getFlSnList());

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
                .build();

        productMapper.updateProduct(updated);

        if (req.getFlSnList() != null && !req.getFlSnList().isEmpty()) {
            productImageMapper.deleteByPrdSn(prdSn);
            saveImages(prdSn, req.getFlSnList());
        }

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

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long prdSn) {
        ProductResponse product = productMapper.findProductById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        product.setImageList(productImageMapper.findImagesByPrdSn(prdSn));
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

        ProductComment comment = ProductComment.builder()
                .prdSn(prdSn)
                .usrSn(usrSn)
                .prdCmtTtl(req.getTtl())
                .prdCmtCn(req.getCn())
                .prdCmtRegId(String.valueOf(usrSn))
                .prdCmtUpdtId(String.valueOf(usrSn))
                .build();

        productCommentMapper.insertComment(comment);

        return productCommentMapper.findLatestComments(prdSn, 1).get(0);
    }

    @Transactional(readOnly = true)
    public List<ProductCommentResponse> getComments(Long prdSn) {
        productMapper.findProductById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
        return productCommentMapper.findLatestComments(prdSn, 4);
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

        productMapper.deleteProduct(prdSn, usrSn);
    }
}
