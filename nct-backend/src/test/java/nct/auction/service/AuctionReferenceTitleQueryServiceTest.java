package nct.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import nct.auction.dto.AuctionReferenceTitle;
import nct.auction.mapper.AuctionMapper;

class AuctionReferenceTitleQueryServiceTest {

    @Test
    void returnsTrimmedTitlesForDistinctValidAuctionIds() {
        AuctionMapper mapper = mock(AuctionMapper.class);
        AuctionReferenceTitle row = new AuctionReferenceTitle();
        row.setAuctionId(8806L);
        row.setTitle("  경매 글 제목  ");
        when(mapper.findAuctionReferenceTitles(List.of(8806L))).thenReturn(List.of(row));
        AuctionReferenceTitleQueryService service = new AuctionReferenceTitleQueryService(mapper);

        Map<Long, String> result = service.findTitles(Arrays.asList(8806L, null, -1L, 8806L));

        assertThat(result).containsExactlyEntriesOf(Map.of(8806L, "경매 글 제목"));
        verify(mapper).findAuctionReferenceTitles(List.of(8806L));
    }
}
