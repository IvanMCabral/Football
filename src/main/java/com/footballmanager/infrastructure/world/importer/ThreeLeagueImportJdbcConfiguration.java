package com.footballmanager.infrastructure.world.importer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
class ThreeLeagueImportJdbcConfiguration {

    @Bean
    DataSource threeLeagueImportDataSource(
            @Value("${spring.datasource.url:${spring.r2dbc.url}}") String url,
            @Value("${spring.datasource.username:${spring.r2dbc.username}}") String username,
            @Value("${spring.datasource.password:${spring.r2dbc.password}}") String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(toJdbcUrl(url));
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource threeLeagueImportDataSource) {
        return new JdbcTemplate(threeLeagueImportDataSource);
    }

    @Bean
    PlatformTransactionManager threeLeagueImportTransactionManager(DataSource threeLeagueImportDataSource) {
        return new DataSourceTransactionManager(threeLeagueImportDataSource);
    }

    private static String toJdbcUrl(String url) {
        // The importer keeps one long transaction; use Neon's direct endpoint
        // when the runtime datasource points at the transaction pooler.
        url = url.replace("-pooler.", ".");
        if (url.startsWith("jdbc:")) {
            return url;
        }
        if (url.startsWith("r2dbc:postgresql:")) {
            return "jdbc:postgresql:" + url.substring("r2dbc:postgresql:".length());
        }
        return url;
    }
}
