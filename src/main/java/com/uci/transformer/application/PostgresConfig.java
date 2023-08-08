package com.uci.transformer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.transformer.odk.entity.converters.AssessmentWriteConverter;
import com.uci.utils.PSQL.JsonToMapConverter;
import com.uci.utils.PSQL.MapToJsonConverter;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableR2dbcRepositories
public class PostgresConfig extends AbstractR2dbcConfiguration {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${postgresql.db.host}")
    private String host;
    @Value("${postgresql.db.port}")
    private int port;
    @Value("${spring.r2dbc.name}")
    private String database;
    @Value("${spring.r2dbc.username}")
    private String username;
    @Value("${spring.r2dbc.password}")
    private String password;
    /**
     * R2DBC Connection
     * Pool Configuration
     */
    @Value("${spring.r2dbc.maxIdleTime}")
    private String maxIdleTime;
    @Value("${spring.r2dbc.maxSize}")
    private String maxSize;
//    @Value("${spring.r2dbc.acquireRetry}")
//    private String acquireRetry;
//    @Value("${spring.r2dbc.maxCreateConnectionTime}")
//    private String maxCreateConnectionTime;
//    @Value("${spring.r2dbc.maxLifeTime}")
//    private String maxLifeTime;
//    @Value("${spring.r2dbc.maxAcquireTime}")
//    private String maxAcquireTime;
    @Value("${spring.r2dbc.initialSize}")
    private String initialSize;

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
//        return new PostgresqlConnectionFactory(
//                PostgresqlConnectionConfiguration.builder()
//                .host(host)
//                .port(port)
//                .username(username)
//                .password(password)
//                .database(database)
//                .build());
        PostgresqlConnectionConfiguration postgresConfig = PostgresqlConnectionConfiguration.builder()
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .database(database)
                .build();
        ConnectionFactory connectionFactory = new PostgresqlConnectionFactory(postgresConfig);
        ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration.builder(connectionFactory)
                .name("aggregatedb-pool")
                .initialSize(Integer.parseInt(initialSize))
                .maxSize(Integer.parseInt(maxSize))
                .maxIdleTime(Duration.ofMillis(Integer.parseInt(maxIdleTime)))
//                .maxCreateConnectionTime(Duration.ofSeconds(Integer.parseInt(maxCreateConnectionTime)))
//                .acquireRetry(Integer.parseInt(acquireRetry))
//                .maxLifeTime(Duration.ofSeconds(Integer.parseInt(maxLifeTime)))
//                .maxAcquireTime(Duration.ofSeconds(Integer.parseInt(maxAcquireTime)))
                .build();
        return new ConnectionPool(poolConfig);
    }

    @Bean
    @Override
    public R2dbcCustomConversions r2dbcCustomConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new JsonToMapConverter(objectMapper));
        converters.add(new MapToJsonConverter(objectMapper));
        converters.add(new AssessmentWriteConverter());
        return new R2dbcCustomConversions(getStoreConversions(), converters);
    }
}