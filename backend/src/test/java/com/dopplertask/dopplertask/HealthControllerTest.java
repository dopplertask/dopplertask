package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.controller.HealthController;
import org.apache.activemq.broker.BrokerService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class HealthControllerTest {

    private DataSource workingDatabase() throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.isValid(Mockito.anyInt())).thenReturn(true);
        return dataSource;
    }

    private BrokerService startedBroker() throws Exception {
        BrokerService broker = new BrokerService();
        broker.setBrokerName("health-test");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.start();
        return broker;
    }

    @Test
    public void testAWorkingServerIsUpSuccess() throws Exception {
        BrokerService broker = startedBroker();
        try {
            ResponseEntity<Map<String, Object>> answer = new HealthController(workingDatabase(), broker).health();
            Assert.assertEquals(200, answer.getStatusCode().value());
            Assert.assertEquals("up", answer.getBody().get("status"));
        } finally {
            broker.stop();
        }
    }

    @Test
    public void testAFullQueueIsDownEvenThoughEverythingElseAnswersFail() throws Exception {
        BrokerService broker = startedBroker();
        try {
            broker.getSystemUsage().getMemoryUsage().setLimit(1);
            broker.getSystemUsage().getMemoryUsage().increaseUsage(10);
            ResponseEntity<Map<String, Object>> answer = new HealthController(workingDatabase(), broker).health();
            Assert.assertEquals(503, answer.getStatusCode().value());
            Assert.assertEquals(List.of("The queue for runs is full, so new runs cannot start."), answer.getBody().get("problems"));
        } finally {
            broker.stop();
        }
    }

    @Test
    public void testADatabaseThatDoesNotAnswerIsDownFail() throws Exception {
        BrokerService broker = startedBroker();
        try {
            DataSource dataSource = Mockito.mock(DataSource.class);
            Mockito.when(dataSource.getConnection()).thenThrow(new SQLException("no connection"));
            Assert.assertEquals(503, new HealthController(dataSource, broker).health().getStatusCode().value());
        } finally {
            broker.stop();
        }
    }
}
