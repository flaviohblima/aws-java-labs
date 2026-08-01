package br.com.flaviohblima.lab65batchmigration;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableBatchProcessing
@EnableJdbcJobRepository
public class Lab65BatchMigrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(Lab65BatchMigrationApplication.class, args);
	}

}
