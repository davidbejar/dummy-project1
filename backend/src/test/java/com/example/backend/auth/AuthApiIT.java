package com.example.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.testsupport.Browser;
import com.example.backend.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The real browser flow against CockroachDB, without Spring Security test shortcuts: fetch a
 * CSRF cookie, log in with a seeded user, use the session, log out.
 */
@SpringBootTest
@IntegrationTest
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

		assertThat(admin.getRole().getCode()).isEqualTo(Role.ADMIN);
		assertThat(admin.getRole().getDisplayName()).isEqualTo("Administrator");
		assertThat(admin.getDisplayName()).isEqualTo("Administrator");
		assertThat(david.getRole().getCode()).isEqualTo(Role.USER);
		assertThat(david.getDisplayName()).isEqualTo("David Bejar");
		for (AppUser user : new AppUser[] { admin, david }) {
			assertThat(user.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(user.getUsername() + "}");
			assertThat(passwordEncoder.matches(user.getUsername(), user.getPasswordHash())).isTrue();
			assertThat(user.isEnabled()).isTrue();
		}
	}

	@Test
	void fullLoginSessionLogoutFlow() throws Exception {
		Browser browser = new Browser(mvc);

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
		new Browser(mvc).login("admin", "admin")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Administrator"))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.roleDisplayName").value("Administrator"));
	}

	@Test
	void usernameIsCaseInsensitive() throws Exception {
		new Browser(mvc).login("David", "david").andExpect(status().isOk()).andExpect(jsonPath("$.username").value("david"));
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		Browser browser = new Browser(mvc);

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

}
