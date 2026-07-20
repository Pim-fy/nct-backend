// Claude Code 작성 (BJN, 2026-07-17)
package nct.file.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.file.domain.FileMeta;

/**
 * [파일 - MyBatis 매퍼]
 * - SQL 본문은 resources/mapper/file/FileMapper.xml
 */
@Mapper
public interface FileMapper {

    /** FILES 행 추가 */
    int insert(FileMeta fileMeta);

    /** FILES 행 조회 — 소프트 삭제(FL_USE_YN='N')된 행은 제외 */
    Optional<FileMeta> findById(@Param("flSn") Long flSn);

    /** 파일 교체 시 메타 갱신 — 같은 행(flSn 유지)의 파일 정보만 새 파일 것으로 바꾼다 */
    int updateMeta(FileMeta fileMeta);

    /** 소프트 삭제 — 행은 이력 추적을 위해 남기고 FL_USE_YN만 'N'으로 */
    int softDelete(@Param("flSn") Long flSn, @Param("updtId") String updtId);

    /**
     * 이 파일을 참조 중인 PRODUCT_IMAGE 행 수 — 등록 완료된 상품의 이미지를 지우면
     * 화면이 깨지므로 삭제 전 가드로 사용 (0이 아니면 삭제 거부)
     */
    int countProductImageRefs(@Param("flSn") Long flSn);

    /**
     * 해당 제공자 신청 건에 이 파일이 실제 연결돼 있는지 (관리자 서류 열람 가드)
     * - 0이면 열람 거부 — flSn만 추측해 다른 파일을 여는 시도를 차단
     * - PROVIDER_APPLY_FILE은 타 담당자(7) 소유 — 읽기 전용 조회만, 변경 금지
     */
    int countProviderApplyFileLink(@Param("prvAplySn") Long prvAplySn, @Param("flSn") Long flSn);
}
