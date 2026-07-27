package nct.auction.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuctionImageItem {

    private Long imageId;
    private Long fileId;
    private String originalName;
    private String path;
    private Character representative;
    private Integer sortNo;
}
