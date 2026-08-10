package nct.member.port;

import java.util.Collection;
import java.util.Map;

import nct.member.dto.AdminMemberIdentityResponse;

/**
 * 담당자 7 · F-OPS-002: 다른 관리자 조회 도메인이 USERS를 직접 조회하지 않고
 * 비민감 회원 식별정보를 일괄 소비하는 읽기 전용 계약입니다.
 */
public interface AdminMemberIdentityReader {

    Map<Long, AdminMemberIdentityResponse> findByUserSns(Collection<Long> userSns);
}
