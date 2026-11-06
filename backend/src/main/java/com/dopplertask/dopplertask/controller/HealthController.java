package com.dopplertask.dopplertask.controller;

import org.apache.activemq.broker.BrokerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * For whatever watches the server. The pages can load while runs cannot start, so this looks at what runs depend on.
 */
@RestController
public class HealthController {

    private static final int DATABASE_SECONDS = 5;

    private final DataSource dataSource;
    private final BrokerService broker;

    public HealthController(DataSource dataSource, BrokerService broker) {
        this.dataSource = dataSource;
        this.broker = broker;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        List<String> problems = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(DATABASE_SECONDS)) {
                problems.add("The database does not answer.");
            }
        } catch (SQLException e) {
            problems.add("The database does not answer.");
        }
        if (!broker.isStarted()) {
            problems.add("The queue for runs is not started.");
        } else if (broker.getSystemUsage().getMemoryUsage().isFull()) {
            problems.add("The queue for runs is full, so new runs cannot start.");
        }
        return problems.isEmpty()
                ? ResponseEntity.ok(Map.of("status", "up"))
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "down", "problems", problems));
    }
}
