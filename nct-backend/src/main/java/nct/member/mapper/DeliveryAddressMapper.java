package nct.member.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import nct.member.domain.DeliveryAddress;

@Mapper
public interface DeliveryAddressMapper {

    List<DeliveryAddress> findActiveByUserId(@Param("userId") Long userId);

    DeliveryAddress findOwnedActiveById(
            @Param("userId") Long userId,
            @Param("deliveryAddressId") Long deliveryAddressId);

    DeliveryAddress findOwnedById(
            @Param("userId") Long userId,
            @Param("deliveryAddressId") Long deliveryAddressId);

    DeliveryAddress findDefaultActiveByUserId(@Param("userId") Long userId);

    Long findFirstActiveId(@Param("userId") Long userId);

    int countActiveByUserId(@Param("userId") Long userId);

    int insert(DeliveryAddress address);

    int update(DeliveryAddress address);

    int clearDefaults(
            @Param("userId") Long userId,
            @Param("actor") String actor);

    int setDefault(
            @Param("userId") Long userId,
            @Param("deliveryAddressId") Long deliveryAddressId,
            @Param("actor") String actor);

    int softDelete(
            @Param("userId") Long userId,
            @Param("deliveryAddressId") Long deliveryAddressId,
            @Param("actor") String actor);
}
