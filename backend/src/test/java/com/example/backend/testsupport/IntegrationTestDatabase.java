package com.example.backend.testsupport;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Creates the integration-test database before Flyway runs. CockroachDB accepts connections
 * to a database that doesn't exist yet, so the configured URL can be used as is.
 */
class IntegrationTestDatabase implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		Environment env = context.getEnvironment();
		String url = env.getRequiredProperty("spring.datasource.url");
		String database = URI.create(url.substring("jdbc:".length())).getPath().substring(1);
		if (!database.matches("[a-z_][a-z0-9_]*")) {
			throw new IllegalStateException("Unexpected test database name: " + database);
		}
		if (database.equals("app")) {
			throw new IllegalStateException("Integration tests must not use the developer's app database");
		}
		try (Connection connection = DriverManager.getConnection(url, env.getProperty("spring.datasource.username"),
				env.getProperty("spring.datasource.password"));
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE DATABASE IF NOT EXISTS " + database);
		}
		catch (SQLException ex) {
			throw new IllegalStateException("Could not create the test database " + database
					+ " (is CockroachDB running? podman compose up -d)", ex);
		}
	}

}
