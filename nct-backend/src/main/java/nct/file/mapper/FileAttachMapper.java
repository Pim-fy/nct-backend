// Claude Code 작성 (HSK → BJN 소유 계약으로 이관, 2026-07-20)
package nct.file.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.file.domain.FileAttach;

/**
 * [파일 - FILE_ATTACH 매퍼] (담당자6)
 * - SQL은 resources/mapper/file/FileAttachMapper.xml
 */
@Mapper
public interface FileAttachMapper {

    /** 첨부 연결 1건 저장 */
    int insertAttach(FileAttach attach);

    /** 참조 건에 붙은 파일 경로(FL_PATH) 목록 - 정렬순서 기준 */
    List<String> selectFilePathsByRef(@Param("refTypeCd") String refTypeCd, @Param("refSn") long refSn);
}
