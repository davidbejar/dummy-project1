package com.example.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.testsupport.IntegrationTest;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Cookie behaviour over real HTTP against the embedded server and CockroachDB; this is
 * exactly what a browser sees.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@IntegrationTest
class AuthHttpIT {

	@LocalServerPort
	private int port;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void anonymousRequestsDoNotCreateSessions() throws Exception {
		HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/todos")).GET());

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(setCookie(response, "JSESSIONID")).isEmpty();
	}

	@Test
	void csrfCookieIsReadableByJavaScript() throws Exception {
		String cookie = setCookie(send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET()), "XSRF-TOKEN").orElseThrow();

		assertThat(cookie).doesNotContainIgnoringCase("HttpOnly");
	}

	@Test
	void loginIssuesHttpOnlySameSiteSessionCookieAndRotatesCsrfToken() throws Exception {
		String csrf = cookieValue(setCookie(send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET()), "XSRF-TOKEN").orElseThrow());

		HttpResponse<String> login = send(HttpRequest.newBuilder(uri("/api/auth/login"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.header("Cookie", "XSRF-TOKEN=" + csrf)
			.header("X-XSRF-TOKEN", csrf)
			.POST(HttpRequest.BodyPublishers.ofString(form("username", "david", "password", "david"))));

		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(login.body()).contains("\"displayName\":\"David Bejar\"").doesNotContain("bcrypt");
		String session = setCookie(login, "JSESSIONID").orElseThrow();
		assertThat(session).containsIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Lax");

		// The CSRF token is replaced at login, so a token captured before login stops working.
		String rotated = cookieValue(setCookie(login, "XSRF-TOKEN").orElseThrow());
		assertThat(rotated).isNotEqualTo(csrf);

		HttpResponse<String> me = send(HttpRequest.newBuilder(uri("/api/auth/me")).header("Cookie", cookieValuePair(session)).GET());
		assertThat(me.statusCode()).isEqualTo(200);
		assertThat(me.body()).contains("\"username\":\"david\"");
	}

	private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://127.0.0.1:" + port + path);
	}

	private static Optional<String> setCookie(HttpResponse<?> response, String name) {
		List<String> cookies = response.headers().allValues("Set-Cookie");
		return cookies.stream().filter(c -> c.startsWith(name + "=")).reduce((first, second) -> second);
	}

	private static String cookieValuePair(String setCookie) {
		return setCookie.split(";", 2)[0];
	}

	private static String cookieValue(String setCookie) {
		return cookieValuePair(setCookie).split("=", 2)[1];
	}

	private static String form(String... pairs) {
		StringBuilder body = new StringBuilder();
		for (int i = 0; i < pairs.length; i += 2) {
			body.append(i == 0 ? "" : "&")
				.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8))
				.append('=')
				.append(URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
		}
		return body.toString();
	}

}
