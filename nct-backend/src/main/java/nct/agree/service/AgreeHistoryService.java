package nct.agree.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.agree.domain.AgreeActType;
import nct.agree.domain.AgreeHistory;
import nct.agree.domain.AgreeRef;
import nct.agree.domain.AgreeType;
import nct.agree.mapper.AgreeHistoryMapper;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;

/**
 * Claude Code 작성 (BJN, 2026-07-18)
 *
 * [동의 - 서비스 계약] (담당자6 백종남, F-OPS-017)
 *
 * 입찰·즉시구매·포인트 홀딩·거래 문제 접수·거래 완료 확인 등 "행위 시점의 동의"를
 * 저장하는 공용 계약이다. 각 행위의 담당자(입찰=담당자5, 거래=담당자4 등)는
 * AGREE_HISTORY 테이블을 직접 INSERT하지 않고 record()를 호출한다.
 *
 * 트랜잭션 방침: 호출자 트랜잭션에 합류한다 — 행위가 롤백되면 그 행위의 동의 이력도
 * 함께 사라지는 것이 증적 정합에 맞다 (감사로그와 같은 방침).
 */
@Service
@RequiredArgsConstructor
public class AgreeHistoryService {

    private final AgreeHistoryMapper agreeHistoryMapper;

    /**
     * 행위 시점 동의 이력 기록 (F-OPS-017) — 각 행위 담당자 공용 계약
     *
     * @param usrSn   동의한 회원
     * @param type    무엇에 동의했나 (약관/개인정보/마케팅 — AGRG01)
     * @param actType 어떤 행위 시점이었나 (입찰/홀딩/완료확인 등 — AGRG02)
     * @param agreed  동의 여부 — 거부(false)도 증적으로 남긴다
     * @param ref     동의 대상 참조 (AgreeRef.bid(sn) 등 정적 팩토리로 생성 — 정확히 1개 보장)
     * @return 생성된 동의 이력 일련번호
     */
    @Transactional
    public long record(long usrSn, AgreeType type, AgreeActType actType, boolean agreed, AgreeRef ref) {
        if (ref == null) {
            throw new CustomException(ErrorCode.MISSING_REQUIRED_FIELD,
                    "동의 대상 참조(AgreeRef)는 필수입니다.");
        }

        AgreeHistory history = new AgreeHistory();
        history.setUsrSn(usrSn);
        history.setBidSn(ref.getBidSn());
        history.setAucSn(ref.getAucSn());
        history.setPtLdgSn(ref.getPtLdgSn());
        history.setTrdDspSn(ref.getTrdDspSn());
        history.setTrdSn(ref.getTrdSn());
        history.setAgrTypeCd(type.getCode());
        history.setAgrActTypeCd(actType.getCode());
        history.setAgrHstAgrYn(agreed ? "Y" : "N");
        agreeHistoryMapper.insert(history);
        return history.getAgrHstSn();
    }

    /** 회원별 동의 이력 조회 (최신순 100건) — 분쟁 대응·증적 확인용 */
    public List<AgreeHistory> getListByUser(long usrSn) {
        return agreeHistoryMapper.selectListByUser(usrSn);
    }
}
