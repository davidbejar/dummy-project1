package com.example.backend.auth;

/** What the frontend learns about the signed-in user. Never includes the password hash. */
public record CurrentUser(String username, String displayName, String role, String roleDisplayName) {

	static CurrentUser from(AppUserDetails user) {
		return new CurrentUser(user.username(), user.displayName(), user.role(), user.roleDisplayName());
	}

}
