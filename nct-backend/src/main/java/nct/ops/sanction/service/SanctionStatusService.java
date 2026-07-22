package nct.ops.sanction.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.ops.sanction.mapper.SanctionMapper;
import nct.ops.sanction.port.SanctionStatusReader;

/** @ai_generated F-AUTH-012 담당자5 SANCTION 소유 영역의 제공자 활동 제재 확인 계약 구현체다. */
@Service
@RequiredArgsConstructor
public class SanctionStatusService implements SanctionStatusReader {

    private final SanctionMapper sanctionMapper;

    /**
     * 유효 제재가 존재하면 제공자 전용 업무를 허용하지 않는다.
     *
     * <p>유효 여부의 시간 경계는 Mapper SQL의 DB CURRENT_TIMESTAMP로 판정해 애플리케이션 서버
     * 시계 차이를 피한다.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public void requireNoActiveSanction(Long userSn) {
        if (userSn == null || userSn <= 0) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        if (sanctionMapper.existsActiveSanction(userSn)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
