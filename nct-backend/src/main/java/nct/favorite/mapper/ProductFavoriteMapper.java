package nct.favorite.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.favorite.dto.FavoriteAuctionItem;
import nct.favorite.dto.FavoriteAuctionTarget;

@Mapper
public interface ProductFavoriteMapper {

    int activate(
            @Param("productId") Long productId,
            @Param("userId") Long userId,
            @Param("actor") String actor);

    int deactivate(
            @Param("productId") Long productId,
            @Param("userId") Long userId,
            @Param("actor") String actor);

    boolean existsActive(
            @Param("productId") Long productId,
            @Param("userId") Long userId);

    long countByProduct(@Param("productId") Long productId);

    List<FavoriteAuctionItem> findMyFavorites(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("size") int size);

    long countMyFavorites(@Param("userId") Long userId);

    FavoriteAuctionTarget findFavoriteTarget(@Param("auctionId") Long auctionId);
}
