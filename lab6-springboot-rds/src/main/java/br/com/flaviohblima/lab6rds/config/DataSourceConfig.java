package br.com.flaviohblima.lab6rds.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() throws Exception {
        String endpoint = System.getenv("RDS_ENDPOINT");
        String secretArn = System.getenv("DB_SECRET_ARN");

        String secretJson;
        try (SecretsManagerClient sm = SecretsManagerClient.builder().region(Region.SA_EAST_1).build()) {
            secretJson = sm.getSecretValue(b -> b.secretId(secretArn)).secretString();
        }

        JsonNode secret = new ObjectMapper().readTree(secretJson);

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://" + endpoint + ":5432/postgres");
        ds.setUsername(secret.get("username").asString());
        ds.setPassword(secret.get("password").asString());
        ds.setMaximumPoolSize(10);
        return ds;
    }
}
