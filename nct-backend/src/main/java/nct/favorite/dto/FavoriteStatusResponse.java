package nct.favorite.dto;

public record FavoriteStatusResponse(
        boolean favorite,
        long favoriteCount
) {
}
