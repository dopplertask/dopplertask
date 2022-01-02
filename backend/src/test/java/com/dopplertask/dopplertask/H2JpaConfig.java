package com.dopplertask.dopplertask;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EntityScan(basePackages = {"com.dopplertask.dopplertask"})
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.dopplertask.dopplertask")
@TestPropertySource({"classpath:application-test.properties"})
public class H2JpaConfig {
    @Value("${spring.datasource.driver-class-name}")
    private String dsDriverClassName;

    @Value("${spring.datasource.url}")
    private String dsUrl;

    @Value("${spring.datasource.username}")
    private String dsUserName;

    @Value("${spring.datasource.password}")
    private String dsPassword;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String hibernateHbm2DllAuto;


    @Bean(name = "dataSource")
    public DataSource getDataSource() {
        DataSource dataSource = DataSourceBuilder.create().username(dsUserName).password(dsPassword).url(dsUrl).driverClassName(dsDriverClassName).build();
        return dataSource;
    }
}