package nct.auction.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.auction.dto.MyBidHistoryItem;

/**
 * [BID 테이블 조회 전용 Mapper] (F-AUC-022 내 입찰 내역 조회)
 * - AUCTION/BID 의 고정 기술 소유권은 09_기능단위_7인_업무분장 v10(CHG-018)부터 담당자5로
 *   이관됐다. 담당자3(본인)은 F-AUC-022 하나만 남았고, 이 매퍼도 그 조회 하나만 남겼다.
 * - 07_기능단위_7인_업무분장 v10 섹션 7.1: "내 입찰 담당자3은 BID를 직접 조회·변경하지 않고
 *   담당자5의 경매·입찰 조회 계약을 호출한다." → 원칙적으로는 이 매퍼도 담당자5가 제공할
 *   조회 계약 호출로 교체되어야 한다. 그 계약이 아직 없어 당장은 기존 조회 쿼리를 유지한다
 *   (TODO: 담당자5가 "내 입찰 조회" 계약을 제공하면 이 매퍼 대신 그 계약을 호출하도록 교체).
 */
@Mapper
public interface BidMapper {

    /**
     * [F-AUC-022 내 입찰 내역 조회] 특정 회원의 입찰 내역을 최신순으로 조회한다.
     * - BID 와 AUCTION 을 JOIN 해서 "지금 이 입찰이 최고입찰/낙찰/반환 중 무엇인지" 판단에
     *   필요한 경매 상태까지 한 번에 가져온다 (N+1 쿼리 방지).
     * - usrSn 은 반드시 컨트롤러에서 로그인한 본인의 회원번호만 전달해야 한다
     *   ("본인 입찰 내역만 조회" 규칙은 다른 사람의 usrSn 을 애초에 받지 않는 방식으로 지킨다).
     */
    List<MyBidHistoryItem> findMyBidHistory(@Param("usrSn") Long usrSn);
}
