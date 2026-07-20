package nct.auction;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import nct.auction.dto.AuctionDetailResponse;
import nct.auction.service.AuctionService;

@SpringBootTest
@AutoConfigureMockMvc
class AuctionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuctionService auctionService;

    @Test
    void allowsAnonymousAuctionDetailLookup() throws Exception {
        AuctionDetailResponse detail = new AuctionDetailResponse();
        detail.setAuctionId(1L);
        detail.setProductId(10L);
        detail.setTitle("공개 경매");
        detail.setFavorite(false);
        detail.setFavoriteCount(3);

        when(auctionService.findAuctionDetail(1L, null)).thenReturn(detail);

        mockMvc.perform(get("/api/auctions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorite").value(false))
                .andExpect(jsonPath("$.data.favoriteCount").value(3));
    }

    @Test
    void rejectsAnonymousFavoriteStatusLookup() throws Exception {
        mockMvc.perform(get("/api/auctions/1/favorite"))
                .andExpect(status().isUnauthorized());
    }
}
