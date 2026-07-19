// Claude Code 작성 (BJN, 2026-07-17)
package nct.file.mapper;

import org.apache.ibatis.annotations.Mapper;

import nct.file.domain.FileMeta;

/**
 * [파일 - MyBatis 매퍼]
 * - SQL 본문은 resources/mapper/file/FileMapper.xml
 */
@Mapper
public interface FileMapper {

    /** FILES 행 추가 */
    int insert(FileMeta fileMeta);
}
