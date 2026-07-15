package nct.auction.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auction.domain.Bid;

/**
 * [BID 테이블 전용 Mapper]
 * - 고정 기술 소유자: 담당자3
 */
@Mapper
public interface BidMapper {

    /**
     * 새 입찰을 기록한다.
     * - useGeneratedKeys 로 INSERT 후 생성된 PK 를 bid.bidSn 에 채워준다 (MemberMapper.saveMember 패턴과 동일).
     * - 로직정의서 F-AUC-014: "실행 후 사용자 직접 취소는 불가" -> 그래서 이 Mapper 에는
     *   deleteBid() 나 cancelBid() 같은 메서드를 아예 만들지 않는다.
     *   (할 수 없게 막는 가장 확실한 방법은 "그 기능 자체를 코드에 존재하지 않게" 하는 것이다.)
     */
    void insertBid(Bid bid);

    /**
     * 경매의 "현재 유효한 최고 입찰" 1건을 조회한다.
     * - 불변식: 한 경매에 ACTIVE 상태의 BID 는 항상 최대 1건이다 (서비스 코드가 이 불변식을 유지).
     * - 이 경매의 첫 입찰이라면 결과가 없다(Optional.empty) - "밀려날 이전 입찰이 없다"는 뜻이다.
     */
    Optional<Bid> findActiveBid(@Param("aucSn") Long aucSn, @Param("activeStatusCd") String activeStatusCd);

    /** 이전 최고 입찰의 상태를 OUTBID 로 변경한다 (포인트 반환은 PointLedgerPort 가 별도로 처리). */
    void updateBidStatus(@Param("bidSn") Long bidSn, @Param("statusCd") String statusCd);
}
