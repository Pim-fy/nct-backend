package nct.product.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.product.domain.Product;
import nct.product.dto.ProductResponse;

@Mapper
public interface ProductMapper {

    void saveProduct(Product product);

    Optional<ProductResponse> findProductById(@Param("prdSn") Long prdSn);

    List<ProductResponse> findMyProducts(@Param("usrSn") Long usrSn, @Param("prdStatusCd") String prdStatusCd);

    Optional<Product> findProductEntityById(@Param("prdSn") Long prdSn);

    void deleteProduct(@Param("prdSn") Long prdSn, @Param("usrSn") Long usrSn);
}
