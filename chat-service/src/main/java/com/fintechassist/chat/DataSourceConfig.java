package com.fintechassist.chat;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Two databases: MySQL (JPA: chats + messages) and Postgres/pgvector (embeddings only).
 * Defining both explicitly keeps the vector store off the MySQL connection.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    DataSource dataSource(@Value("${spring.datasource.url}") String url,
                          @Value("${spring.datasource.username}") String user,
                          @Value("${spring.datasource.password}") String pass) {
        return org.springframework.boot.jdbc.DataSourceBuilder.create().url(url).username(user).password(pass).build();
    }

    @Bean("vectorDataSource")
    DataSource vectorDataSource(@Value("${vector.datasource.url}") String url,
                                @Value("${vector.datasource.username}") String user,
                                @Value("${vector.datasource.password}") String pass) {
        return org.springframework.boot.jdbc.DataSourceBuilder.create().url(url).username(user).password(pass).build();
    }

    @Bean("vectorJdbcTemplate")
    JdbcTemplate vectorJdbcTemplate(@Qualifier("vectorDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    /** all-MiniLM-L6-v2 produces 384-dimensional vectors. */
    @Bean
    PgVectorStore vectorStore(@Qualifier("vectorJdbcTemplate") JdbcTemplate jdbc, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbc, embeddingModel)
                .dimensions(384)
                .initializeSchema(true)
                .build();
    }
}
