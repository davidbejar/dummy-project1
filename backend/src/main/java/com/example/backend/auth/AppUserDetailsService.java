package com.example.backend.auth;

import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository users;

	public AppUserDetailsService(AppUserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		// Usernames are stored lowercase, so "David" and "david" are the same account.
		return users.findByUsername(username.strip().toLowerCase(Locale.ROOT))
			.map(AppUserDetails::from)
			.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

}
