package nct.common.file.mapper;

import org.apache.ibatis.annotations.Mapper;

import nct.common.file.domain.StoredFile;

/** [공통 - FILES 매퍼] SQL은 resources/mapper/common/FilesMapper.xml */
@Mapper
public interface FilesMapper {

    /** 업로드 파일 메타데이터 1건 저장 */
    int insertFile(StoredFile file);
}
