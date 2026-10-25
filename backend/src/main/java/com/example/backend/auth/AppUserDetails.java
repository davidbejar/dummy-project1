package com.example.backend.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal kept in the HTTP session. Holds a snapshot of the user, not
 * the JPA entity, so it is safe to serialize.
 */
public record AppUserDetails(UUID id, String username, String password, String displayName, Role role,
		boolean enabled) implements UserDetails {

	static AppUserDetails from(AppUser user) {
		return new AppUserDetails(user.getId(), user.getUsername(), user.getPasswordHash(), user.getDisplayName(),
				user.getRole(), user.isEnabled());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(role.authority()));
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public String toString() {
		// Never print the password hash.
		return "AppUserDetails[username=" + username + ", role=" + role + "]";
	}

}
