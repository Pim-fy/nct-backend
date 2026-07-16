package nct.ops.notice.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.ops.notice.domain.Notice;

/**
 * NOTICE 테이블의 공개 조회 전용 MyBatis 연결부다.
 * 게시 상태·사용 여부·노출 기간 필터는 XML SQL에서 항상 적용한다.
 */
@Mapper
public interface NoticeMapper {

    long countPublicNotices(@Param("typeCode") String typeCode);

    List<Notice> findPublicNotices(@Param("typeCode") String typeCode,
                                   @Param("offset") long offset,
                                   @Param("size") int size);

    Optional<Notice> findPublicNoticeById(@Param("noticeId") Long noticeId);
}
