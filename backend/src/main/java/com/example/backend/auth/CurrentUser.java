package com.example.backend.auth;

import java.util.List;

/**
 * What the frontend learns about the signed-in user, including the codes of the screens they
 * may open. Never includes the password hash.
 */
public record CurrentUser(String username, String displayName, String role, String roleDisplayName,
		List<String> screens) {

	static CurrentUser from(AppUserDetails user, ScreenAccess screens) {
		return new CurrentUser(user.username(), user.displayName(), user.role(), user.roleDisplayName(),
				screens.screensOf(user.role()));
	}

}
