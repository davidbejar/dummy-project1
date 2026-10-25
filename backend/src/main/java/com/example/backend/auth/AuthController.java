package com.example.backend.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and logout are handled by Spring Security filters configured in {@link SecurityConfig}
 * ({@code POST /api/auth/login}, {@code POST /api/auth/logout}).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	static final String CSRF_URL = "/api/auth/csrf";

	/**
	 * Issues the {@code XSRF-TOKEN} cookie. Tokens are created lazily, so the frontend calls
	 * this before its first state-changing request (typically the login) when it has none.
	 */
	@GetMapping("/csrf")
	public ResponseEntity<Void> csrf(CsrfToken token) {
		token.getToken(); // reading the token makes Spring Security write the cookie
		return ResponseEntity.noContent().build();
	}

	/** The signed-in user; 401 when there is no session (handled by Spring Security). */
	@GetMapping("/me")
	public CurrentUser me(@AuthenticationPrincipal AppUserDetails user) {
		return CurrentUser.from(user);
	}

}
