package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.service.RemovedStepUpgrade;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class RemovedStepUpgradeTest {
    @Test
    public void testSavedStepFailsWithAReasonSuccess() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:removedstep;DB_CLOSE_DELAY=-1");
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE action (id BIGINT PRIMARY KEY, action_type VARCHAR(255), fail_on VARCHAR(255))");
            statement.execute("CREATE TABLE print_action (id BIGINT PRIMARY KEY, message VARCHAR(255))");
            statement.execute("CREATE TABLE credential (name VARCHAR(255), step_type VARCHAR(255))");
            statement.execute("CREATE TABLE rockmelon_action (id BIGINT PRIMARY KEY, api_key VARCHAR(255))");
            statement.execute("CREATE TABLE rockmelon_parameter (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO action VALUES (1, 'rockmelon_action', NULL), (2, 'print_action', NULL)");
            statement.execute("INSERT INTO rockmelon_action VALUES (1, 'key')");
            statement.execute("INSERT INTO credential VALUES ('old', 'RockmelonAction'), ('kept', 'SlackAction')");

            RemovedStepUpgrade.upgrade(database);

            try (ResultSet step = statement.executeQuery("SELECT action_type, fail_on FROM action WHERE id = 1")) {
                step.next();
                Assert.assertEquals("print_action", step.getString(1));
                Assert.assertTrue(step.getString(2).contains("Rockmelon"));
            }
            try (ResultSet other = statement.executeQuery("SELECT fail_on FROM action WHERE id = 2")) {
                other.next();
                Assert.assertNull(other.getString(1));
            }
            try (ResultSet message = statement.executeQuery("SELECT message FROM print_action WHERE id = 1")) {
                Assert.assertTrue(message.next());
            }
            try (ResultSet secrets = statement.executeQuery("SELECT name FROM credential")) {
                secrets.next();
                Assert.assertEquals("kept", secrets.getString(1));
                Assert.assertFalse(secrets.next());
            }
            try (ResultSet gone = connection.getMetaData().getTables(null, null, "ROCKMELON_ACTION", null)) {
                Assert.assertFalse(gone.next());
            }
            // Nothing to do the second time.
            RemovedStepUpgrade.upgrade(database);
        }
    }
}
