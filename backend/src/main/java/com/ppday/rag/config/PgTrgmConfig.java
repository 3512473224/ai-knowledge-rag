package com.ppday.rag.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 关键词检索依赖 pg_trgm 扩展（官方 postgres 镜像自带 contrib），
 * 启动时幂等创建，避免 DBA 手动执行。
 */
@Configuration
public class PgTrgmConfig {

    @Bean
    CommandLineRunner enablePgTrgm(JdbcTemplate jdbcTemplate) {
        return args -> jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
    }
}
