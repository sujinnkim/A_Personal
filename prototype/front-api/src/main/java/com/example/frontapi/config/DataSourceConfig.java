package com.example.frontapi.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * AS-08: 커넥션 풀 분리 설정
 * - joinDataSource    : 입장 전용 (maximumPoolSize=100, timeout=3s)
 * - serviceDataSource : 회의 관리 (maximumPoolSize=40, timeout=5s)
 * - generalDataSource : 일반/권한 (maximumPoolSize=60, timeout=5s)
 * - queryDataSource   : Replica Read (maximumPoolSize=80, timeout=3s)
 */
@Configuration
public class DataSourceConfig {

    // ── 공통 설정 ─────────────────────────────────────────────────
    @Value("${datasource.join.jdbc-url}")      private String joinUrl;
    @Value("${datasource.join.username}")      private String joinUser;
    @Value("${datasource.join.password}")      private String joinPassword;
    @Value("${datasource.join.maximum-pool-size:100}") private int joinMaxPoolSize;
    @Value("${datasource.join.minimum-idle:20}")       private int joinMinIdle;
    @Value("${datasource.join.connection-timeout:3000}") private long joinConnTimeout;

    @Value("${datasource.service.jdbc-url}")   private String serviceUrl;
    @Value("${datasource.service.username}")   private String serviceUser;
    @Value("${datasource.service.password}")   private String servicePassword;
    @Value("${datasource.service.maximum-pool-size:40}") private int serviceMaxPoolSize;
    @Value("${datasource.service.minimum-idle:10}")      private int serviceMinIdle;
    @Value("${datasource.service.connection-timeout:5000}") private long serviceConnTimeout;

    @Value("${datasource.general.jdbc-url}")   private String generalUrl;
    @Value("${datasource.general.username}")   private String generalUser;
    @Value("${datasource.general.password}")   private String generalPassword;
    @Value("${datasource.general.maximum-pool-size:60}") private int generalMaxPoolSize;
    @Value("${datasource.general.minimum-idle:10}")      private int generalMinIdle;
    @Value("${datasource.general.connection-timeout:5000}") private long generalConnTimeout;

    @Value("${datasource.query.jdbc-url}")     private String queryUrl;
    @Value("${datasource.query.username}")     private String queryUser;
    @Value("${datasource.query.password}")     private String queryPassword;
    @Value("${datasource.query.maximum-pool-size:80}") private int queryMaxPoolSize;
    @Value("${datasource.query.minimum-idle:10}")      private int queryMinIdle;
    @Value("${datasource.query.connection-timeout:3000}") private long queryConnTimeout;

    // ── Join Pool ─────────────────────────────────────────────────

    @Bean
    public HikariDataSource joinDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(joinUrl);
        ds.setUsername(joinUser);
        ds.setPassword(joinPassword);
        ds.setDriverClassName("org.mariadb.jdbc.Driver");
        ds.setMaximumPoolSize(joinMaxPoolSize);
        ds.setMinimumIdle(joinMinIdle);
        ds.setConnectionTimeout(joinConnTimeout);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1_800_000);
        ds.setPoolName("JoinPool");
        return ds;
    }

    // ── Service Pool ──────────────────────────────────────────────

    @Bean
    public HikariDataSource serviceDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(serviceUrl);
        ds.setUsername(serviceUser);
        ds.setPassword(servicePassword);
        ds.setDriverClassName("org.mariadb.jdbc.Driver");
        ds.setMaximumPoolSize(serviceMaxPoolSize);
        ds.setMinimumIdle(serviceMinIdle);
        ds.setConnectionTimeout(serviceConnTimeout);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1_800_000);
        ds.setPoolName("ServicePool");
        return ds;
    }

    // ── General Pool ──────────────────────────────────────────────

    @Bean
    public HikariDataSource generalDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(generalUrl);
        ds.setUsername(generalUser);
        ds.setPassword(generalPassword);
        ds.setDriverClassName("org.mariadb.jdbc.Driver");
        ds.setMaximumPoolSize(generalMaxPoolSize);
        ds.setMinimumIdle(generalMinIdle);
        ds.setConnectionTimeout(generalConnTimeout);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1_800_000);
        ds.setPoolName("GeneralPool");
        return ds;
    }

    // ── Query Pool (Replica Read) ─────────────────────────────────

    @Bean
    public HikariDataSource queryDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(queryUrl);
        ds.setUsername(queryUser);
        ds.setPassword(queryPassword);
        ds.setDriverClassName("org.mariadb.jdbc.Driver");
        ds.setMaximumPoolSize(queryMaxPoolSize);
        ds.setMinimumIdle(queryMinIdle);
        ds.setConnectionTimeout(queryConnTimeout);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1_800_000);
        ds.setPoolName("QueryPool");
        ds.setReadOnly(true);
        return ds;
    }

    // ── Routing DataSource (AS-07 CQRS) ──────────────────────────

    @Bean
    @Primary
    public DataSource routingDataSource() {
        RoutingDataSource routingDataSource = new RoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.JOIN, joinDataSource());
        targetDataSources.put(DataSourceType.SERVICE, serviceDataSource());
        targetDataSources.put(DataSourceType.GENERAL, generalDataSource());
        targetDataSources.put(DataSourceType.QUERY, queryDataSource());

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(serviceDataSource());
        return routingDataSource;
    }

    // ── JPA EntityManagerFactory ──────────────────────────────────

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(routingDataSource());
        em.setPackagesToScan("com.example.frontapi");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(true);
        em.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        properties.setProperty("hibernate.format_sql", "true");
        em.setJpaProperties(properties);

        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
