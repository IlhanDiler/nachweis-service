package de.diler.nachweis;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
public class OracleTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	public OracleContainer oracleContainer() {
		// Oracle braucht beim ersten Start deutlich laenger als PostgreSQL.
		return new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
				.withStartupTimeout(Duration.ofMinutes(5));
	}

}
