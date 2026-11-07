package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.dao.ActionDao;
import com.dopplertask.dopplertask.domain.action.connection.HttpAction;
import com.dopplertask.dopplertask.domain.action.connection.SSHAction;
import com.dopplertask.dopplertask.domain.action.connection.SecureCopyAction;
import com.dopplertask.dopplertask.domain.action.connection.MySQLAction;
import com.dopplertask.dopplertask.domain.action.integration.jenkins.JenkinsAction;
import com.dopplertask.dopplertask.domain.action.trigger.Webhook;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.boot.model.naming.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Moves secrets that an earlier version stored inside steps into credentials, and removes the columns they were in.
 * It finds nothing to do once that has happened, so it can stay until nobody upgrades from before credentials.
 */
@Service
public class LegacySecretMigration {

    private static final Logger LOG = LoggerFactory.getLogger(LegacySecretMigration.class);
    // In these steps also the fields that are not secret were encrypted, and webhooks encrypted nothing.
    private static final Set<Class<?>> ALL_FIELDS_ENCRYPTED = Set.of(SSHAction.class, SecureCopyAction.class, MySQLAction.class, JenkinsAction.class);

    private final JdbcTemplate database;
    private final CredentialService credentials;
    private final ColumnEncryptor legacyEncryptor;
    private final ActionDao actionDao;

    public LegacySecretMigration(JdbcTemplate database, CredentialService credentials, ColumnEncryptor legacyEncryptor, ActionDao actionDao) {
        this.database = database;
        this.credentials = credentials;
        this.legacyEncryptor = legacyEncryptor;
        this.actionDao = actionDao;
    }

    @Transactional
    public void migrate() {
        CredentialService.stepTypes().values().forEach(this::migrateStepsOf);
        for (HttpAction http : actionDao.findHttpActionsWithSecretHeaders()) {
            String before = String.join(",", headerNames(http));
            credentials.adoptInlineSecrets(List.of(http), false);
            // The headers of a step are not removed by replacing its list.
            if (http.getCredential() != null && http.getAuthHeaderName() == null && !before.equals(String.join(",", headerNames(http)))) {
                actionDao.saveAndFlush(http);
                database.update("DELETE FROM http_header WHERE http_action_id = ? AND header_name NOT IN (" + keptPlaceholders(http) + ")", keptArguments(http));
            }
        }
    }

    private void migrateStepsOf(Class<?> stepClass) {
        if (stepClass == HttpAction.class) {
            return;
        }
        Map<String, Boolean> fields = CredentialService.fieldsOf(stepClass);
        String table = physicalName(stepClass.isAnnotationPresent(Table.class) ? stepClass.getAnnotation(Table.class).name() : stepClass.getSimpleName());
        Map<String, String> columns = new LinkedHashMap<>();
        fields.keySet().forEach(field -> {
            if (CredentialService.isOnlyInCredentials(stepClass, field) && columnExists(table, physicalName(field))) {
                columns.put(field, physicalName(field));
            }
        });
        if (columns.isEmpty()) {
            return;
        }
        boolean moved;
        try {
            moved = moveRows(stepClass, table, columns, fields);
        } catch (IllegalStateException e) {
            // Nothing is removed, so that the right key can move it at the next start.
            LOG.error("Secrets in {} could not be read, and stay where they are. Check DOPPLERTASK_ENCRYPTION_KEY.", table);
            return;
        }
        columns.values().forEach(column -> database.execute("ALTER TABLE " + table + " DROP COLUMN " + column));
        if (moved) {
            LOG.info("Moved the secrets in {} into credentials.", table);
        }
    }

    private boolean moveRows(Class<?> stepClass, String table, Map<String, String> columns, Map<String, Boolean> fields) {
        boolean moved = false;
        List<Map<String, Object>> rows = database.queryForList("SELECT id, " + String.join(", ", columns.values()) + " FROM " + table);
        for (Map<String, Object> row : rows) {
            Map<String, String> values = new LinkedHashMap<>();
            columns.forEach((field, column) -> {
                Object stored = row.get(column);
                if (stored != null && !stored.toString().isBlank()) {
                    boolean encrypted = stepClass != Webhook.class && (fields.get(field) || ALL_FIELDS_ENCRYPTED.contains(stepClass));
                    values.put(field, encrypted ? legacyEncryptor.convertToEntityAttribute(stored.toString()) : stored.toString());
                }
            });
            if (!values.isEmpty()) {
                database.update("UPDATE action SET credential = ? WHERE id = ?", credentials.findOrCreate(stepClass.getSimpleName(), values), row.get("id"));
                moved = true;
            }
        }
        return moved;
    }

    private List<String> headerNames(HttpAction http) {
        List<String> names = new ArrayList<>();
        http.getHeaders().forEach(header -> names.add(header.getHeaderName()));
        return names;
    }

    private String keptPlaceholders(HttpAction http) {
        return http.getHeaders().isEmpty() ? "''" : String.join(", ", http.getHeaders().stream().map(header -> "?").toList());
    }

    private Object[] keptArguments(HttpAction http) {
        List<Object> arguments = new ArrayList<>();
        arguments.add(http.getId());
        arguments.addAll(headerNames(http));
        return arguments.toArray();
    }

    private boolean columnExists(String table, String column) {
        Integer found = database.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?", Integer.class,
                table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT));
        return found != null && found > 0;
    }

    // The same naming that created the tables, as guessing it would leave secrets behind.
    private static String physicalName(String name) {
        return new PhysicalNamingStrategySnakeCaseImpl().toPhysicalTableName(Identifier.toIdentifier(name), null).getText();
    }
}
