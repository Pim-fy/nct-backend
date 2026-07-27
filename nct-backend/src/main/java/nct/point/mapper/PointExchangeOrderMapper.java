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

    /** 관리자 처리용 — 행 잠금 조회 (두 관리자가 같은 건을 동시에 처리하는 것 차단) */
    PointExchangeOrder selectForUpdateBySn(@Param("ptExcOrdSn") long ptExcOrdSn);

    /** 관리자 처리 대기 목록 — 신청 상태 건만, 신청자 이름 조인 (오래된 것부터: 먼저 신청한 사람 먼저 지급) */
    List<PointExchangeOrder> selectRequestedListForAdmin();

    /** 지급 완료 처리 — 상태·처리자·처리일시 기록 */
    int complete(@Param("ptExcOrdSn") long ptExcOrdSn, @Param("statusCd") String statusCd,
                 @Param("procUsrSn") long procUsrSn);

    /** 반려 처리 — 상태·처리자·처리일시 + 복원 원장 SN·반려 사유 기록 */
    int reject(@Param("ptExcOrdSn") long ptExcOrdSn, @Param("statusCd") String statusCd,
               @Param("procUsrSn") long procUsrSn, @Param("restoreLdgSn") long restoreLdgSn,
               @Param("rejectReason") String rejectReason);
}
