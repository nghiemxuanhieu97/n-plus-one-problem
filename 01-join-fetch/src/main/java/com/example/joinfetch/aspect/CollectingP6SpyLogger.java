package com.example.joinfetch.aspect;

import com.p6spy.engine.logging.Category;
import com.p6spy.engine.spy.appender.P6Logger;

import java.util.ArrayList;
import java.util.List;

public class CollectingP6SpyLogger implements P6Logger {

    private static final ThreadLocal<List<String>> SQL_STATEMENTS =
            new ThreadLocal<>();

    public static void begin() {
        SQL_STATEMENTS.set(new ArrayList<>());
    }

    public static List<String> end() {
        List<String> statements = SQL_STATEMENTS.get();

        SQL_STATEMENTS.remove();

        if (statements == null) {
            return List.of();
        }

        return List.copyOf(statements);
    }

    @Override
    public void logSQL(
            int connectionId,
            String now,
            long elapsed,
            Category category,
            String preparedSql,
            String sqlWithValues,
            String url
    ) {
        if (!Category.STATEMENT.equals(category)) {
            return;
        }

        if (sqlWithValues == null || sqlWithValues.isBlank()) {
            return;
        }

        List<String> statements = SQL_STATEMENTS.get();

        // Chỉ thu SQL khi BenchmarkAspect đã gọi begin().
        if (statements != null) {
            statements.add(sqlWithValues);
        }
    }

    @Override
    public void logException(Exception exception) {
        // Không cần xử lý trong benchmark collector.
    }

    @Override
    public void logText(String text) {
        // Không cần xử lý.
    }

    @Override
    public boolean isCategoryEnabled(Category category) {
        return Category.STATEMENT.equals(category);
    }
}