package nct.servicerequest.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.servicerequest.domain.SvcReqImage;
import nct.servicerequest.dto.SvcReqImageItem;

@Mapper
public interface SvcReqImageMapper {

    /** 요청서 등록 시 이미지 목록 일괄 추가 */
    void insertAll(@Param("images") List<SvcReqImage> images);

    /** 요청서 이미지 전체 삭제 — 수정 시 재삽입 전 호출 */
    void deleteBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    /** 요청서 이미지 목록 조회 — 상세 조회·임시저장 복원용 */
    List<SvcReqImageItem> findImagesBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    /** 재등록(마감된 요청서 복사) 전용 — 원본 행을 그대로 복제하기 위한 전체 컬럼 조회 */
    List<SvcReqImage> findImageEntitiesBySvcReqSn(@Param("svcReqSn") Long svcReqSn);

    int countImageLink(@Param("svcReqSn") Long svcReqSn, @Param("flSn") Long flSn);
}
