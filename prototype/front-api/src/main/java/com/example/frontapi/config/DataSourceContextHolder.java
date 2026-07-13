package com.example.frontapi.config;

/**
 * 현재 스레드에서 사용할 DataSource 타입을 보관하는 ThreadLocal 컨텍스트
 */
public class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT = new ThreadLocal<>();

    public static void set(DataSourceType type) {
        CONTEXT.set(type);
    }

    public static DataSourceType get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
