package com.dopplertask.dopplertask.service;

import com.zaxxer.hikari.HikariDataSource;
import org.h2.api.ErrorCode;
import org.h2.tools.Upgrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * The database files of H2 before version 2 cannot be opened by what is used now, so such a file is converted before
 * anything connects to it. H2 does this with its own earlier version, which it fetches from Maven Central, and it
 * keeps the old file.
 */
@Component
public class OldDatabaseUpgrade implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(OldDatabaseUpgrade.class);
    private static final int OLD_H2_VERSION = 199;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof HikariDataSource dataSource && isOldFile(dataSource)) {
            upgrade(dataSource);
        }
        return bean;
    }

    private boolean isOldFile(HikariDataSource dataSource) {
        String url = dataSource.getJdbcUrl();
        if (url == null || !url.startsWith("jdbc:h2:") || url.startsWith("jdbc:h2:mem:")) {
            return false;
        }
        try {
            DriverManager.getConnection(url, dataSource.getUsername(), dataSource.getPassword()).close();
            return false;
        } catch (SQLException e) {
            return e.getErrorCode() == ErrorCode.FILE_VERSION_ERROR_1;
        }
    }

    private void upgrade(HikariDataSource dataSource) {
        String url = dataSource.getJdbcUrl();
        Properties login = new Properties();
        login.setProperty("user", dataSource.getUsername());
        login.setProperty("password", dataSource.getPassword() == null ? "" : dataSource.getPassword());
        try {
            LOG.info("Converting {} to the new database format. H2 keeps the old file with the ending .mv.db.bak.", url);
            if (!Upgrade.upgrade(url, login, OLD_H2_VERSION)) {
                throw new IllegalStateException("H2 found nothing to convert.");
            }
            LOG.info("The database was converted.");
        } catch (Exception e) {
            throw new IllegalStateException("The database at " + url + " has an old format and could not be converted. The conversion fetches H2 " + OLD_H2_VERSION + " from Maven Central, so check that this server can reach it.", e);
        }
    }
}
