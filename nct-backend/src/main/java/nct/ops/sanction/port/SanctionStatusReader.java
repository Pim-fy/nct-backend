package nct.ops.sanction.port;

/**
 * SANCTION 고정 소유 영역이 다른 도메인에 제공하는 최소 읽기 계약이다.
 *
 * <p>소비자는 SANCTION Mapper나 SQL을 직접 사용하지 않고 이 계약만 호출한다. 이 계약은
 * 제공자 전용 기능 차단에만 사용하며, 일반 로그인·계정 전체 인증 차단은 USERS 상태 계약의
 * 책임으로 남는다.</p>
 *
 * @ai_generated F-AUTH-012 SANCTION 읽기 소유권 경계를 고정한다.
 */
public interface SanctionStatusReader {

    /** 현재 시각에 유효한 제재가 있으면 접근을 차단한다. */
    void requireNoActiveSanction(Long userSn);
}
