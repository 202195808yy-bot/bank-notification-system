package com.bank.customer.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时同步 customers 表的 PostgreSQL 自增序列，
 * 避免手动插入数据后序列落后导致主键冲突。
 */
@Component
public class SequenceSynchronizer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public SequenceSynchronizer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        syncSequence("customers", "id");
    }

    private void syncSequence(String tableName, String idColumn) {
        String seqName = tableName + "_" + idColumn + "_seq";  // PostgreSQL 默认序列命名
        String sql = "SELECT setval(?, COALESCE((SELECT MAX(" + idColumn + ") FROM " + tableName + "), 0))";
        jdbcTemplate.queryForObject(sql, Long.class, seqName);
    }
}
