package com.example.backend.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Runs a {@code @SpringBootTest} against the {@code app_test} database (the {@code it}
 * profile) instead of the developer's {@code app} database, creating it first if needed.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("it")
@ContextConfiguration(initializers = IntegrationTestDatabase.class)
public @interface IntegrationTest {
}
