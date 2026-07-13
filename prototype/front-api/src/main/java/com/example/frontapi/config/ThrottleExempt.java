package com.example.frontapi.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AS-06 설계원칙 2(핵심 면제): 이 어노테이션이 붙은 핸들러(또는 컨트롤러)는
 * 피크 구간에도 {@link ThrottlingInterceptor}의 유입 제한에서 제외된다.
 * 핵심 API(회의 입장·conference-token 발급·회의 시작)에 적용한다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ThrottleExempt {
}
