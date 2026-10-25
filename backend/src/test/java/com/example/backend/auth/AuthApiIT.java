package com.example.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The real browser flow against CockroachDB, without Spring Security test shortcuts: fetch a
 * CSRF cookie, log in with a seeded user, use the session, log out.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void seededUsersExistWithHashedPasswords() {
		AppUser admin = users.findByUsername("admin").orElseThrow();
		AppUser david = users.findByUsername("david").orElseThrow();

		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.getDisplayName()).isEqualTo("Administrator");
		assertThat(david.getRole()).isEqualTo(Role.USER);
		assertThat(david.getDisplayName()).isEqualTo("David Bejar");
		for (AppUser user : new AppUser[] { admin, david }) {
			assertThat(user.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(user.getUsername() + "}");
			assertThat(passwordEncoder.matches(user.getUsername(), user.getPasswordHash())).isTrue();
			assertThat(user.isEnabled()).isTrue();
		}
	}

	@Test
	void fullLoginSessionLogoutFlow() throws Exception {
		Browser browser = new Browser();

		browser.send(get("/api/auth/me")).andExpect(status().isUnauthorized());
		browser.send(get("/api/todos")).andExpect(status().isUnauthorized());

		browser.login("david", "david")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("david"))
			.andExpect(jsonPath("$.displayName").value("David Bejar"))
			.andExpect(jsonPath("$.role").value("USER"));

		browser.send(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("David Bejar"));
		browser.send(get("/api/todos")).andExpect(status().isOk());

		browser.send(post("/api/auth/logout")).andExpect(status().isNoContent());

		browser.send(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void adminCanLogIn() throws Exception {
		new Browser().login("admin", "admin")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Administrator"))
			.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void usernameIsCaseInsensitive() throws Exception {
		new Browser().login("David", "david").andExpect(status().isOk()).andExpect(jsonPath("$.username").value("david"));
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		Browser browser = new Browser();

		browser.login("david", "admin")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid username or password"));
		browser.send(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithoutCsrfTokenIsForbidden() throws Exception {
		mvc.perform(post("/api/auth/login").param("username", "david").param("password", "david"))
			.andExpect(status().isForbidden());
	}

	/** Keeps the session and the XSRF-TOKEN cookie between requests, like a browser. */
	private class Browser {

		private final MockHttpSession session = new MockHttpSession();

		private String csrfToken;

		org.springframework.test.web.servlet.ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
			request.session(session);
			if (csrfToken != null) {
				request.cookie(new Cookie("XSRF-TOKEN", csrfToken)).header("X-XSRF-TOKEN", csrfToken);
			}
			var actions = mvc.perform(request);
			MvcResult result = actions.andReturn();
			Cookie issued = result.getResponse().getCookie("XSRF-TOKEN");
			if (issued != null) {
				csrfToken = issued.getMaxAge() == 0 ? null : issued.getValue();
			}
			return actions;
		}

		org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
			if (csrfToken == null) {
				send(get("/api/auth/csrf")).andExpect(status().isNoContent());
			}
			return send(post("/api/auth/login").param("username", username).param("password", password));
		}

	}

}
