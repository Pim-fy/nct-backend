package nct.servicerequest.mapper;

import java.util.List;
import nct.servicerequest.domain.SvcReqComment;
import nct.servicerequest.dto.SvcReqCommentResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SvcReqCommentMapper {

    void insertComment(SvcReqComment comment);

    List<SvcReqCommentResponse> findLatestComments(@Param("svcReqSn") Long svcReqSn, @Param("limit") int limit);
}
