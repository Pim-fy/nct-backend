package nct.product.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import nct.global.dto.PagedResponse;
import nct.global.exception.CustomException;
import nct.global.exception.ErrorCode;
import nct.product.domain.Product;
import nct.product.domain.ProductImage;
import nct.product.dto.ProductRegisterRequest;
import nct.product.dto.ProductResponse;
import nct.product.mapper.ProductImageMapper;
import nct.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;

    @Transactional
    public ProductResponse registerProduct(Long usrSn, ProductRegisterRequest req) {
        String statusCd = (req.getPrdStatusCd() != null) ? req.getPrdStatusCd() : "PRDC0002";
        Product product = Product.builder()
                .usrSn(usrSn)
                .catSn(req.getCatSn())
                .prdNm(req.getPrdNm())
                .prdCn(req.getPrdCn())
                .prdStatusCd(statusCd)
                .prdStartAmt(req.getPrdStartAmt())
                .prdIbyAmt(req.getPrdIbyAmt())
                .prdTrdMethodCd(req.getPrdTrdMethodCd())
                .prdRegId(String.valueOf(usrSn))
                .prdUpdtId(String.valueOf(usrSn))
                .build();

        productMapper.saveProduct(product);
        saveImages(product.getPrdSn(), req.getFlSnList());

        return productMapper.findProductById(product.getPrdSn())
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    // 업로드된 파일 id 목록을 PRODUCT_IMAGE로 연결 — 목록 순서상 첫 번째가 대표(F-AUC-002)
    private void saveImages(Long prdSn, List<Long> flSnList) {
        if (flSnList == null || flSnList.isEmpty()) return;

        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < flSnList.size(); i++) {
            images.add(ProductImage.builder()
                    .prdSn(prdSn)
                    .flSn(flSnList.get(i))
                    .prdImgRprsYn(i == 0 ? "Y" : "N")
                    .prdImgSortNo(i)
                    .build());
        }
        productImageMapper.insertAll(images);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long prdSn) {
        return productMapper.findProductById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> getMyProducts(Long usrSn, int page, int size) {
        PageHelper.startPage(page, size);
        List<ProductResponse> list = productMapper.findMyProducts(usrSn);
        return PagedResponse.of(new PageInfo<>(list));
    }

    @Transactional
    public void deleteProduct(Long prdSn, Long usrSn) {
        Product product = productMapper.findProductEntityById(prdSn)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getUsrSn().equals(usrSn)) {
            throw new CustomException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        productMapper.deleteProduct(prdSn, usrSn);
    }
}
