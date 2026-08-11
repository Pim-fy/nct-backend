package nct.quote.port;

import java.util.List;

/**
 * 서비스 요청에 견적을 제출한 제공자 usrSn 조회 계약.
 * 담당자2(신현석) — 요청서 변경사항 추가 시 제공자 알림 발행에 소비한다.
 */
public interface ServiceRequestQuoteProviderReader {

    /** 해당 서비스 요청에 견적을 제출한 제공자 USR_SN distinct 목록 (철회된 견적 포함). */
    List<Long> findProviderUsrSnBySvcReqSn(Long svcReqSn);
}
