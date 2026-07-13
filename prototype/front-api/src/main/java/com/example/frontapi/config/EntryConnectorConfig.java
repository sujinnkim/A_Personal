package com.example.frontapi.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AS-04: 입장 전용 Tomcat Connector 추가
 * - port 8081 : maxThreads=200, minSpareThreads=50 (입장 전용)
 * - port 8080 : maxThreads=300 (일반) — application.yml에서 설정
 *
 * 두 Connector는 동일 Spring Context를 공유하므로,
 * Nginx가 경로 기반으로 /join·/conference-token → 8081, 나머지 → 8080 라우팅
 */
@Configuration
public class EntryConnectorConfig {

    @Value("${entry.connector.port:8081}")
    private int entryPort;

    @Value("${entry.connector.max-threads:200}")
    private int maxThreads;

    @Value("${entry.connector.min-spare-threads:50}")
    private int minSpareThreads;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> entryConnectorCustomizer() {
        return factory -> {
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setPort(entryPort);
            connector.setProperty("maxThreads", String.valueOf(maxThreads));
            connector.setProperty("minSpareThreads", String.valueOf(minSpareThreads));
            connector.setProperty("acceptCount", "100");
            connector.setProperty("connectionTimeout", "20000");
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
