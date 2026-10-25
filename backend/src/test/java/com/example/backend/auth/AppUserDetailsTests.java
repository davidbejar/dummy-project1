package com.example.backend.auth;

import static com.example.backend.testsupport.Entities.disabledUser;
import static com.example.backend.testsupport.Entities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** The signed-in principal, and how it is loaded at login. */
class AppUserDetailsTests {

	private final Role admin = new Role(Role.ADMIN, "Administrator");

	private final AppUserRepository users = mock(AppUserRepository.class);

	private final AppUserDetailsService service = new AppUserDetailsService(users);

	@Test
	void copiesTheUserAndTheirRole() {
		AppUser user = user("admin", admin);

		assertThat(AppUserDetails.from(user)).isEqualTo(new AppUserDetails(user.getId(), "admin", "{noop}admin",
				"Name of admin", Role.ADMIN, "Administrator", true));
	}

	@Test
	void actsAsASpringSecurityUser() {
		AppUserDetails details = AppUserDetails.from(user("admin", admin));

		assertThat(details.getUsername()).isEqualTo("admin");
		assertThat(details.getPassword()).isEqualTo("{noop}admin");
		assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
		assertThat(details.isEnabled()).isTrue();
		assertThat(AppUserDetails.from(disabledUser("gone", admin)).isEnabled()).isFalse();
	}

	@Test
	void neverPrintsThePasswordHash() {
		assertThat(AppUserDetails.from(user("admin", admin))).hasToString("AppUserDetails[username=admin, role=ADMIN]");
	}

	@Test
	void loadsUsersByTheirLowercaseName() {
		given(users.findByUsername("admin")).willReturn(Optional.of(user("admin", admin)));

		assertThat(service.loadUserByUsername("  Admin ").getUsername()).isEqualTo("admin");
	}

	@Test
	void reportsUnknownUsers() {
		given(users.findByUsername("nobody")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.loadUserByUsername("nobody")).isInstanceOf(UsernameNotFoundException.class)
			.hasMessage("User not found");
	}

}
