package nct.product.mapper;

import java.util.List;
import nct.product.domain.ProductComment;
import nct.product.dto.ProductCommentResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductCommentMapper {

    void insertComment(ProductComment comment);

    List<ProductCommentResponse> findLatestComments(@Param("prdSn") Long prdSn, @Param("limit") int limit);
}
