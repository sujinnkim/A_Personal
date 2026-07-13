package com.example.frontapi.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * AS-07 CQRS: @Transactional(readOnly=true) 시 QueryPool(Replica)로 라우팅
 * DataSourceContextHolder에 설정된 DataSourceType에 따라 풀을 선택한다.
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType type = DataSourceContextHolder.get();
        if (type == null) {
            return DataSourceType.SERVICE;
        }
        return type;
    }
}
