package nct.favorite.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.favorite.dto.FavoriteAuctionItem;
import nct.favorite.dto.FavoriteAuctionListResponse;
import nct.favorite.dto.FavoriteStatusResponse;
import nct.favorite.dto.FavoriteAuctionTarget;
import nct.favorite.mapper.ProductFavoriteMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class ProductFavoriteService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 60;
    private static final String USE_Y = "Y";
    private static final String DELETED_PRODUCT_STATUS = "PRDC0004";

    private final ProductFavoriteMapper productFavoriteMapper;

    @Transactional
    public FavoriteStatusResponse addFavorite(Long auctionId, Long userId) {
        FavoriteAuctionTarget target = requireFavoriteTarget(auctionId);
        validateFavoritableProduct(target, userId);

        productFavoriteMapper.activate(target.getProductId(), userId, actor(userId));
        return status(target.getProductId(), userId);
    }

    @Transactional
    public FavoriteStatusResponse removeFavorite(Long auctionId, Long userId) {
        FavoriteAuctionTarget target = requireFavoriteTarget(auctionId);
        validateUsableProduct(target);

        productFavoriteMapper.deactivate(target.getProductId(), userId, actor(userId));
        return status(target.getProductId(), userId);
    }

    @Transactional(readOnly = true)
    public FavoriteStatusResponse getFavoriteStatus(Long auctionId, Long userId) {
        FavoriteAuctionTarget target = requireFavoriteTarget(auctionId);
        validateUsableProduct(target);

        return status(target.getProductId(), userId);
    }

    @Transactional(readOnly = true)
    public FavoriteAuctionListResponse getMyFavorites(Long userId, int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;

        long totalElements = productFavoriteMapper.countMyFavorites(userId);
        List<FavoriteAuctionItem> items = totalElements > 0
                ? productFavoriteMapper.findMyFavorites(userId, offset, normalizedSize)
                : List.of();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / normalizedSize);

        return FavoriteAuctionListResponse.builder()
                .items(items)
                .page(normalizedPage)
                .size(normalizedSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    private FavoriteStatusResponse status(Long productId, Long userId) {
        return new FavoriteStatusResponse(
                productFavoriteMapper.existsActive(productId, userId),
                productFavoriteMapper.countByProduct(productId));
    }

    private FavoriteAuctionTarget requireFavoriteTarget(Long auctionId) {
        FavoriteAuctionTarget target = productFavoriteMapper.findFavoriteTarget(auctionId);
        if (target == null) {
            throw new CustomException(ErrorCode.AUCTION_NOT_FOUND);
        }
        return target;
    }

    private void validateFavoritableProduct(FavoriteAuctionTarget target, Long userId) {
        validateUsableProduct(target);
        if (target.getSellerId() != null && target.getSellerId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateUsableProduct(FavoriteAuctionTarget target) {
        if (!USE_Y.equals(target.getProductUseYn()) || DELETED_PRODUCT_STATUS.equals(target.getProductStatusCode())) {
            throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, DEFAULT_PAGE);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String actor(Long userId) {
        return userId.toString();
    }
}
