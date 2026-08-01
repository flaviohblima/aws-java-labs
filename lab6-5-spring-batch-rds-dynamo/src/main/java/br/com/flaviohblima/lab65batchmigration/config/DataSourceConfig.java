package br.com.flaviohblima.lab65batchmigration.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Value("${lab65-batch-migration.rds_endpoint}")
    private String endpoint;

    @Value("${lab65-batch-migration.db_secret_arn}")
    private String secretArn;

    @Bean
    public DataSource dataSource() throws Exception {
        String secretJson;
        try (SecretsManagerClient sm = SecretsManagerClient.create()) {
            secretJson = sm.getSecretValue(b -> b.secretId(secretArn)).secretString();
        }

        JsonNode secret = new ObjectMapper().readTree(secretJson);

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://" + endpoint + ":5432/postgres");
        ds.setUsername(secret.get("username").asText());
        ds.setPassword(secret.get("password").asText());
        ds.setMaximumPoolSize(10);
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }
}
