package nct.global.idempotency;

import java.time.LocalDateTime;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// @ai_generated: [전역 중복요청 방지 - REQ_FGPT MyBatis 매퍼] (F-COM-017)
// - SQL 본문은 resources/mapper/global/RequestFingerprintMapper.xml
// - 조회 화면이 없는 순수 인프라 저장소라 별도 도메인 클래스 없이 Map으로 주고받는다
@Mapper
public interface RequestFingerprintMapper {

    /** 신규 지문 등록 시도. UNIQUE(RQF_HASH) 위반 시 DuplicateKeyException으로 변환되어 전파된다 */
    int tryInsert(@Param("hash") String hash, @Param("expDt") LocalDateTime expDt);

    /** 기존 지문의 저장된 응답 조회 (RQF_RESP_STAT, RQF_RESP_BODY 키) */
    Map<String, Object> selectByHash(@Param("hash") String hash);

    /** 처리 완료 후 응답 저장 */
    int updateResponse(@Param("hash") String hash, @Param("status") int status, @Param("body") String body);

    /** 만료 지문 배치 삭제 (1회 최대 1000행) */
    int deleteExpired(@Param("now") LocalDateTime now);
}
