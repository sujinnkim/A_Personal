package com.example.frontapi.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

/**
 * AS-07 CQRS: @Transactional(readOnly=true) 시 QueryPool로 자동 라우팅
 * @Order(1) — TransactionInterceptor(@Order(Integer.MAX_VALUE))보다 먼저 실행되어
 *             커넥션 획득 전에 DataSource를 결정한다.
 */
@Aspect
@Component
@Order(1)
public class DataSourceRoutingAspect {

    @Around("@annotation(transactional)")
    public Object routeDataSource(ProceedingJoinPoint pjp, Transactional transactional) throws Throwable {
        DataSourceType previous = DataSourceContextHolder.get();
        try {
            if (transactional.readOnly()) {
                DataSourceContextHolder.set(DataSourceType.QUERY);
            }
            return pjp.proceed();
        } finally {
            if (previous == null) {
                DataSourceContextHolder.clear();
            } else {
                DataSourceContextHolder.set(previous);
            }
        }
    }
}
