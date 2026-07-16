package nct.ops.notice.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.ops.notice.domain.Notice;
import nct.ops.notice.domain.AdminNoticeWriteCommand;

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

    long countAdminNotices(@Param("typeCode") String typeCode,
                           @Param("statusCode") String statusCode,
                           @Param("keyword") String keyword);

    List<Notice> findAdminNotices(@Param("typeCode") String typeCode,
                                  @Param("statusCode") String statusCode,
                                  @Param("keyword") String keyword,
                                  @Param("offset") long offset,
                                  @Param("size") int size);

    Optional<Notice> findAdminNoticeById(@Param("noticeId") Long noticeId);

    Optional<Notice> findAdminNoticeByIdForUpdate(@Param("noticeId") Long noticeId);

    int insertAdminNotice(AdminNoticeWriteCommand command);

    int updateAdminNotice(AdminNoticeWriteCommand command);

    int hideAdminNotice(@Param("noticeId") Long noticeId,
                        @Param("actorId") String actorId);

    int softDeleteAdminNotice(@Param("noticeId") Long noticeId,
                              @Param("actorId") String actorId);
}
