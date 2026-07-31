package nct.trade.port;

import nct.trade.dto.ServiceTradeCreateCommand;
import nct.trade.dto.ServiceTradeCreateResult;

/** 담당자4 서비스 거래 생성 계약의 포트다. */
public interface ServiceTradeCreator {

    ServiceTradeCreateResult createOrGetServiceTrade(ServiceTradeCreateCommand command);
}
