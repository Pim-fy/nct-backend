package nct.favorite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import nct.auction.constant.AuctionStatusCode;
import nct.favorite.dto.FavoriteAuctionListResponse;
import nct.favorite.dto.FavoriteStatusResponse;
import nct.favorite.service.ProductFavoriteService;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

@SpringBootTest
class ProductFavoriteServiceTest {

    @Autowired ProductFavoriteService favoriteService;
    @Autowired JdbcTemplate jdbc;

    final List<Long> userIds = new ArrayList<>();
    final List<Long> productIds = new ArrayList<>();
    final List<Long> auctionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ensureSchema();
    }

    @AfterEach
    void cleanUp() {
        if (!productIds.isEmpty()) {
            jdbc.update("DELETE FROM PRODUCT_FAVORITE WHERE PRD_SN IN (" + joinIds(productIds) + ")");
        }
        if (!auctionIds.isEmpty()) {
            jdbc.update("DELETE FROM BID WHERE AUC_SN IN (" + joinIds(auctionIds) + ")");
            jdbc.update("DELETE FROM AUCTION WHERE AUC_SN IN (" + joinIds(auctionIds) + ")");
        }
        if (!productIds.isEmpty()) {
            jdbc.update("DELETE FROM PRODUCT WHERE PRD_SN IN (" + joinIds(productIds) + ")");
        }
        if (!userIds.isEmpty()) {
            String ids = joinIds(userIds);
            jdbc.update("DELETE FROM NOTIFICATION WHERE USR_SN IN (" + ids + ")");
            jdbc.update("DELETE FROM USERS WHERE USR_SN IN (" + ids + ")");
        }
    }

    @Test
    @DisplayName("관심 등록은 같은 사용자의 중복 요청을 멱등 성공으로 처리한다")
    void addFavoriteIsIdempotent() {
        long sellerSn = insertUser("t_fav_seller");
        long buyerSn = insertUser("t_fav_buyer");
        long aucSn = insertActiveAuction(sellerSn, "관심 등록 상품");

        FavoriteStatusResponse first = favoriteService.addFavorite(aucSn, buyerSn);
        FavoriteStatusResponse second = favoriteService.addFavorite(aucSn, buyerSn);

        assertThat(first.favorite()).isTrue();
        assertThat(first.favoriteCount()).isEqualTo(1);
        assertThat(second.favorite()).isTrue();
        assertThat(second.favoriteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("관심 해제는 사용중 여부를 N으로 바꾸고 집계에서 제외한다")
    void removeFavoriteDeactivates() {
        long sellerSn = insertUser("t_fav_seller");
        long buyerSn = insertUser("t_fav_buyer");
        long aucSn = insertActiveAuction(sellerSn, "관심 해제 상품");
        favoriteService.addFavorite(aucSn, buyerSn);

        FavoriteStatusResponse removed = favoriteService.removeFavorite(aucSn, buyerSn);

        assertThat(removed.favorite()).isFalse();
        assertThat(removed.favoriteCount()).isZero();
    }

    @Test
    @DisplayName("판매자는 본인 상품을 관심 등록할 수 없다")
    void sellerCannotFavoriteOwnProduct() {
        long sellerSn = insertUser("t_fav_seller");
        long aucSn = insertActiveAuction(sellerSn, "판매자 본인 상품");

        assertThatThrownBy(() -> favoriteService.addFavorite(aucSn, sellerSn))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("내 관심 경매 목록은 활성 관심만 최신순으로 조회한다")
    void getMyFavorites() {
        long sellerSn = insertUser("t_fav_seller");
        long buyerSn = insertUser("t_fav_buyer");
        long oldAucSn = insertActiveAuction(sellerSn, "이전 관심 상품");
        long latestAucSn = insertActiveAuction(sellerSn, "최신 관심 상품");
        long removedAucSn = insertActiveAuction(sellerSn, "해제 관심 상품");
        favoriteService.addFavorite(oldAucSn, buyerSn);
        favoriteService.addFavorite(latestAucSn, buyerSn);
        favoriteService.addFavorite(removedAucSn, buyerSn);
        favoriteService.removeFavorite(removedAucSn, buyerSn);

        FavoriteAuctionListResponse response = favoriteService.getMyFavorites(buyerSn, 1, 12);

        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getAuctionId()).isEqualTo(latestAucSn);
        assertThat(response.getItems()).extracting("title")
                .containsExactly("최신 관심 상품", "이전 관심 상품");
    }

    private long insertUser(String prefix) {
        String loginId = prefix + "_" + System.nanoTime();
        jdbc.update("""
                INSERT INTO USERS (USR_LOGIN_ID, USR_PSWD_HASH, USR_NM, USR_EML, USR_STATUS_CD, USR_ROLE_CD)
                VALUES (?, '{noop}test', ?, ?, 'USRC0001', 'ROLE_USER')
                """, loginId, prefix, loginId + "@test.local");
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        userIds.add(id);
        return id;
    }

    private long insertActiveAuction(long sellerSn, String productName) {
        long prdSn = insertProduct(sellerSn, productName);
        jdbc.update("""
                INSERT INTO AUCTION (
                    PRD_SN,
                    AUC_STATUS_CD,
                    AUC_CUR_AMT,
                    AUC_BID_UNIT_AMT,
                    AUC_START_DT,
                    AUC_END_DT,
                    AUC_EXT_CNT
                )
                VALUES (?, ?, ?, 1000, ?, ?, 0)
                """,
                prdSn,
                AuctionStatusCode.ACTIVE,
                BigDecimal.valueOf(10000),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1));
        long aucSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        auctionIds.add(aucSn);
        return aucSn;
    }

    private long insertProduct(long sellerSn, String productName) {
        jdbc.update("""
                INSERT INTO PRODUCT (USR_SN, CAT_SN, PRD_NM, PRD_STATUS_CD, PRD_START_AMT, PRD_TRD_METHOD_CD)
                VALUES (?, 2, ?, 'PRDC0002', 10000,
                        (SELECT C.CMM_CD FROM CMM_CODE C
                         JOIN CMM_CODE P ON C.CMM_PARENT_SN = P.CMM_SN
                         WHERE P.CMM_CD = 'TRDG03' ORDER BY C.CMM_SORT_NO LIMIT 1))
                """, sellerSn, productName);
        long prdSn = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        productIds.add(prdSn);
        return prdSn;
    }

    private void ensureSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS PRODUCT_FAVORITE
                (
                    PRD_FAV_SN BIGINT NOT NULL AUTO_INCREMENT COMMENT '상품관심일련번호',
                    PRD_SN BIGINT NOT NULL COMMENT '상품일련번호',
                    USR_SN BIGINT NOT NULL COMMENT '회원일련번호',
                    PRD_FAV_USE_YN CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '상품관심사용여부',
                    PRD_FAV_REG_DT DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
                    PRD_FAV_UPDT_DT DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '갱신일시',
                    PRD_FAV_REG_ID VARCHAR(50) NOT NULL DEFAULT 'SYSTEM' COMMENT '등록자ID',
                    PRD_FAV_UPDT_ID VARCHAR(50) NOT NULL DEFAULT 'SYSTEM' COMMENT '갱신자ID',
                    PRIMARY KEY (PRD_FAV_SN),
                    UNIQUE KEY UK_PRODUCT_FAVORITE_USER_PRODUCT (USR_SN, PRD_SN),
                    KEY IDX_PRODUCT_FAVORITE_PRODUCT (PRD_SN, PRD_FAV_USE_YN),
                    KEY IDX_PRODUCT_FAVORITE_USER (USR_SN, PRD_FAV_USE_YN, PRD_FAV_REG_DT),
                    CONSTRAINT CK_PRODUCT_FAVORITE_USE_YN CHECK (PRD_FAV_USE_YN IN ('Y', 'N')),
                    CONSTRAINT FK_PRODUCT_FAVORITE_PRODUCT FOREIGN KEY (PRD_SN)
                        REFERENCES PRODUCT (PRD_SN) ON DELETE RESTRICT ON UPDATE RESTRICT,
                    CONSTRAINT FK_PRODUCT_FAVORITE_USER FOREIGN KEY (USR_SN)
                        REFERENCES USERS (USR_SN) ON DELETE RESTRICT ON UPDATE RESTRICT
                ) ENGINE = InnoDB
                  DEFAULT CHARSET = utf8mb4
                  COLLATE = utf8mb4_0900_ai_ci
                  COMMENT = '상품 관심'
                """);
        if (!hasColumn("PRODUCT_FAVORITE", "PRD_FAV_USE_YN")) {
            jdbc.execute("""
                    ALTER TABLE PRODUCT_FAVORITE
                    ADD COLUMN PRD_FAV_USE_YN CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '상품관심사용여부'
                    AFTER USR_SN
                    """);
        }
    }

    private String joinIds(List<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }

    private boolean hasColumn(String tableName, String columnName) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
