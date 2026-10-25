package com.example.backend.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private AppUserDetailsService userDetailsService;

	private AppUserDetails david;

	@BeforeEach
	void setUp() {
		david = new AppUserDetails(UUID.randomUUID(), "david", passwordEncoder.encode("david"), "David Bejar", Role.USER,
				true);
		given(userDetailsService.loadUserByUsername(anyString())).willThrow(new UsernameNotFoundException("nope"));
		willReturn(david).given(userDetailsService).loadUserByUsername("david");
	}

	@Test
	void loginWithValidCredentialsReturnsCurrentUser() throws Exception {
		mvc.perform(post("/api/auth/login").with(csrf()).param("username", "david").param("password", "david"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("david"))
			.andExpect(jsonPath("$.displayName").value("David Bejar"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void loginWithWrongPasswordIsRejected() throws Exception {
		mvc.perform(post("/api/auth/login").with(csrf()).param("username", "david").param("password", "wrong"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType("application/problem+json"))
			.andExpect(jsonPath("$.detail").value("Invalid username or password"));
	}

	@Test
	void loginWithUnknownUserGivesTheSameMessage() throws Exception {
		mvc.perform(post("/api/auth/login").with(csrf()).param("username", "nobody").param("password", "x"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid username or password"));
	}

	@Test
	void loginWithoutCsrfTokenIsForbidden() throws Exception {
		mvc.perform(post("/api/auth/login").param("username", "david").param("password", "david"))
			.andExpect(status().isForbidden());
	}

	@Test
	void meWithoutSessionIsUnauthorized() throws Exception {
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	/**
	 * Fresh context: the {@code csrf()} helper used by other tests swaps the CSRF token
	 * repository in the shared context, which would hide the real cookie.
	 */
	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
	void csrfEndpointIssuesReadableTokenCookieWithoutLogin() throws Exception {
		mvc.perform(get("/api/auth/csrf"))
			.andExpect(status().isNoContent())
			.andExpect(cookie().exists("XSRF-TOKEN"))
			.andExpect(cookie().httpOnly("XSRF-TOKEN", false));
	}

	@Test
	void meReturnsSignedInUser() throws Exception {
		mvc.perform(get("/api/auth/me").with(user(david)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("david"))
			.andExpect(jsonPath("$.displayName").value("David Bejar"));
	}

	@Test
	void logoutReturnsNoContent() throws Exception {
		mvc.perform(post("/api/auth/logout").with(user(david)).with(csrf())).andExpect(status().isNoContent());
	}

}
