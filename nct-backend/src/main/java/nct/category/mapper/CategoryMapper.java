package nct.category.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.category.domain.Category;

@Mapper
public interface CategoryMapper {

    List<Category> findCategoriesByDomain(@Param("domainCd") String domainCd);
}
