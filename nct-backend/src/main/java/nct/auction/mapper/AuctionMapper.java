package nct.auction.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auction.domain.Auction;

/**
 * [AUCTION 테이블 전용 Mapper]
 * - 고정 기술 소유자: 담당자3
 * - MemberMapper 와 동일한 패턴: 인터페이스는 메서드 시그니처만, 실제 SQL 은 XML 에 작성.
 */
@Mapper
public interface AuctionMapper {

    /**
     * 입찰 검증(F-AUC-013)을 위해 경매 1건을 조회한다.
     * - 지금 단계에서는 조회만 하므로 FOR UPDATE(잠금) 을 걸지 않는다.
     * - F-AUC-019(즉시구매 충돌 제어)에서 실제로 갱신(UPDATE)까지 하는 시점에는
     *   비관적 락(FOR UPDATE) 또는 버전 컬럼 기반 낙관적 락이 필요해지는데,
     *   그건 013 범위를 벗어나므로 이 메서드와는 별도 메서드로 분리해서 만들 예정이다.
     */
    Optional<Auction> findAuctionForBid(@Param("aucSn") Long aucSn);

    /**
     * [F-AUC-014 입찰 실행 전용] 경매 1건을 "쓰기 잠금"과 함께 조회한다 (SELECT ... FOR UPDATE).
     * - 반드시 @Transactional 메서드 안에서만 호출해야 한다. 트랜잭션 밖에서 호출하면
     *   조회 직후 잠금이 즉시 풀려버려 잠금을 건 의미가 없어진다.
     * - 이 잠금이 필요한 이유: 같은 경매에 두 사용자가 거의 동시에 입찰하면,
     *   둘 다 "현재 최고가 10,000원"을 읽고 각자 자기 입찰이 유효하다고 판단할 수 있다(Lost Update).
     *   FOR UPDATE 는 먼저 들어온 트랜잭션이 커밋될 때까지 다른 트랜잭션의 조회를 대기시켜
     *   "한 번에 한 명만 이 경매를 갱신"하도록 강제한다.
     */
    Optional<Auction> findAuctionForUpdate(@Param("aucSn") Long aucSn);

    /** [F-AUC-014] 새 최고 입찰가로 AUCTION.AUC_CUR_AMT 를 갱신한다. */
    void updateCurrentAmount(@Param("aucSn") Long aucSn, @Param("newAmt") Long newAmt);

    /** [F-AUC-018] 즉시구매 성공 시 경매 상태를 변경한다 (예: 진행중 -> 종료). */
    void updateStatus(@Param("aucSn") Long aucSn, @Param("statusCd") String statusCd);
}
