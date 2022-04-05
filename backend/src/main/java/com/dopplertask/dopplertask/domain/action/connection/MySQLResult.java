package com.dopplertask.dopplertask.domain.action.connection;

import java.util.List;

public class MySQLResult {
    private String query;
    private List<MySQLRow> rows;

    public List<MySQLRow> getRows() {
        return rows;
    }

    public void setRows(List<MySQLRow> rows) {
        this.rows = rows;
    }

    public String getQuery() {
        return query;
    }

    public MySQLResult setQuery(String query) {
        this.query = query;
        return this;
    }

    public static class MySQLColumn {
        private String name;
        private String value;

        public MySQLColumn(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class MySQLRow {
        private List<MySQLColumn> columns;

        public MySQLRow(List<MySQLColumn> columns) {
            this.columns = columns;
        }

        public List<MySQLColumn> getColumns() {
            return columns;
        }

        public void setColumns(List<MySQLColumn> columns) {
            this.columns = columns;
        }
    }
}
