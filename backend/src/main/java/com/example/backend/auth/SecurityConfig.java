package com.example.backend.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Session-based authentication for the single-page app.
 *
 * <ul>
 * <li>{@code POST /api/auth/login} (form fields {@code username}, {@code password}) creates a
 * session; the session id travels in an HttpOnly cookie that JavaScript cannot read.</li>
 * <li>{@code POST /api/auth/logout} ends it.</li>
 * <li>Every other {@code /api/**} endpoint requires a session and answers 401 without one.</li>
 * <li>CSRF protection uses the SPA pattern: the token is in the readable {@code XSRF-TOKEN}
 * cookie and must be echoed in the {@code X-XSRF-TOKEN} header on state-changing requests.
 * {@code GET /api/auth/csrf} issues the cookie, e.g. before the first login.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

	static final String LOGIN_URL = "/api/auth/login";

	static final String LOGOUT_URL = "/api/auth/logout";

	static final String BAD_CREDENTIALS_MESSAGE = "Invalid username or password";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper json) throws Exception {
		http.authorizeHttpRequests(auth -> auth
			.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/error").permitAll()
			.requestMatchers(AuthController.CSRF_URL).permitAll()
			// The OpenAPI description (off when springdoc.api-docs.enabled=false).
			.requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
			.anyRequest().authenticated())
			.csrf(csrf -> csrf.spa())
			.formLogin(form -> form.loginPage("/")
				.loginProcessingUrl(LOGIN_URL)
				.successHandler(loginSuccess(json))
				.failureHandler(loginFailure(json))
				.permitAll())
			.logout(logout -> logout.logoutUrl(LOGOUT_URL)
				.deleteCookies("JSESSIONID")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			// An API answers 401 instead of redirecting to a login page.
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			// Don't create sessions for anonymous requests just to remember their URL; an API
			// has no login redirect to return from. Sessions start only at login.
			.requestCache(cache -> cache.disable())
			.httpBasic(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/** Username/password login checks users from the database, and nothing else. */
	@Bean
	AuthenticationManager authenticationManager(AppUserDetailsService users, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	/** Stores and checks hashes as {@code {bcrypt}...}; the prefix allows upgrading algorithms later. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	private static AuthenticationSuccessHandler loginSuccess(JsonMapper json) {
		return (request, response, authentication) -> writeJson(response, json, HttpStatus.OK,
				MediaType.APPLICATION_JSON_VALUE, CurrentUser.from((AppUserDetails) authentication.getPrincipal()));
	}

	/** Same message for unknown user and wrong password, so usernames cannot be probed. */
	private static AuthenticationFailureHandler loginFailure(JsonMapper json) {
		return (request, response, exception) -> writeJson(response, json, HttpStatus.UNAUTHORIZED,
				MediaType.APPLICATION_PROBLEM_JSON_VALUE,
				Map.of("title", "Unauthorized", "status", 401, "detail", BAD_CREDENTIALS_MESSAGE));
	}

	private static void writeJson(HttpServletResponse response, JsonMapper json, HttpStatus status,
			String contentType, Object body) throws IOException {
		response.setStatus(status.value());
		response.setContentType(contentType);
		json.writeValue(response.getOutputStream(), body);
	}

}
