package nct.point.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.point.domain.PointExchangeOrder;
import nct.point.dto.UserAccount;

/**
 * Claude Code 작성 (BJN, 2026-07-17)
 *
 * [포인트 - 환전 주문 매퍼] (F-PAY-012, D-026)
 */
@Mapper
public interface PointExchangeOrderMapper {

    /** 환전 신청 행 추가 (신청 상태 + 차감 원장 연결 + 계좌 스냅샷) */
    int insert(PointExchangeOrder order);

    /** 내 환전 신청 목록 (최신순 100건, 상태 한글명 조인) */
    List<PointExchangeOrder> selectListByUser(@Param("usrSn") long usrSn);

    /** 신청 시점 계좌 스냅샷용 — 회원의 등록 계좌 조회 (읽기 전용, USERS는 담당자1 소유) */
    UserAccount selectUserAccount(@Param("usrSn") long usrSn);
}
