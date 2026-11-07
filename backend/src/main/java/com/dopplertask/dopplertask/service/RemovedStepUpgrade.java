package com.dopplertask.dopplertask.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * A step that no longer exists would stop a saved task from loading, and an active one would stop the app from starting.
 * It becomes a step that says what happened and fails, so the task can be opened and changed. This runs as soon as the
 * database is there, since schedules start before anything else gets to read it.
 */
@Component
public class RemovedStepUpgrade implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(RemovedStepUpgrade.class);
    private static final String REMOVED_STEP = "rockmelon_action";
    private static final String REMOVED_CREDENTIAL = "RockmelonAction";
    private static final String MESSAGE = "This step used Rockmelon, which DopplerTask no longer has. Remove it from the task.";

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource) {
            upgrade(dataSource);
        }
        return bean;
    }

    public static void upgrade(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (!hasStepTable(connection)) {
                return;
            }
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            List<Long> steps = removedSteps(connection);
            try (PreparedStatement change = connection.prepareStatement("UPDATE action SET action_type = 'print_action', fail_on = ? WHERE id = ?");
                 PreparedStatement message = connection.prepareStatement("INSERT INTO print_action (id, message) VALUES (?, ?)");
                 PreparedStatement secrets = connection.prepareStatement("DELETE FROM credential WHERE step_type = ?");
                 Statement tables = connection.createStatement()) {
                for (Long step : steps) {
                    change.setString(1, MESSAGE);
                    change.setLong(2, step);
                    change.executeUpdate();
                    message.setLong(1, step);
                    message.setString(2, MESSAGE);
                    message.executeUpdate();
                }
                secrets.setString(1, REMOVED_CREDENTIAL);
                secrets.executeUpdate();
                tables.execute("DROP TABLE IF EXISTS rockmelon_parameter");
                tables.execute("DROP TABLE IF EXISTS rockmelon_action");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            LOG.info("Changed {} Rockmelon steps, which no longer exist, to steps that fail and say so.", steps.size());
        } catch (SQLException e) {
            throw new IllegalStateException("Saved Rockmelon steps could not be changed: " + e.getMessage(), e);
        }
    }

    private static boolean hasStepTable(Connection connection) throws SQLException {
        for (String name : new String[]{"rockmelon_action", "ROCKMELON_ACTION"}) {
            try (ResultSet found = connection.getMetaData().getTables(null, null, name, new String[]{"TABLE"})) {
                if (found.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Long> removedSteps(Connection connection) throws SQLException {
        List<Long> steps = new ArrayList<>();
        try (PreparedStatement find = connection.prepareStatement("SELECT id FROM action WHERE action_type = ?")) {
            find.setString(1, REMOVED_STEP);
            try (ResultSet rows = find.executeQuery()) {
                while (rows.next()) {
                    steps.add(rows.getLong(1));
                }
            }
        }
        return steps;
    }
}
