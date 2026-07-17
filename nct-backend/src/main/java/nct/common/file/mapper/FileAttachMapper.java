package nct.common.file.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.common.file.domain.FileAttach;

/** [공통 - FILE_ATTACH 매퍼] SQL은 resources/mapper/common/FileAttachMapper.xml */
@Mapper
public interface FileAttachMapper {

    /** 첨부 연결 1건 저장 */
    int insertAttach(FileAttach attach);

    /** 참조 건에 붙은 파일 경로(FL_PATH) 목록 - 정렬순서 기준 */
    List<String> selectFilePathsByRef(@Param("refTypeCd") String refTypeCd, @Param("refSn") long refSn);
}
