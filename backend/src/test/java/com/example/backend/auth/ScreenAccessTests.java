package com.example.backend.auth;

import static com.example.backend.testsupport.Entities.screen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

class ScreenAccessTests {

	private final AppUserRepository users = mock(AppUserRepository.class);

	private final RoleRepository roles = mock(RoleRepository.class);

	private final ScreenRepository screens = mock(ScreenRepository.class);

	private final RoleScreenRepository roleScreens = mock(RoleScreenRepository.class);

	private final ScreenAccess access = new ScreenAccess(users, roles, screens, roleScreens);

	@Test
	void signedInUsersSeeTheScreensOfTheirRole() {
		given(users.canSee("david", "TODO")).willReturn(true);
		var david = UsernamePasswordAuthenticationToken.authenticated("david", null, List.of());

		assertThat(access.canSee(david, "TODO")).isTrue();
		assertThat(access.canSee(david, "ROLE_ADMIN")).isFalse();
		verify(roleScreens, never()).existsByRoleCodeAndScreenCode(Role.ANONYMOUS, "TODO");
	}

	@Test
	void everyoneElseSeesTheScreensOfTheAnonymousRole() {
		given(roleScreens.existsByRoleCodeAndScreenCode(Role.ANONYMOUS, "LOGIN")).willReturn(true);
		var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
		var notYetChecked = UsernamePasswordAuthenticationToken.unauthenticated("david", "david");

		for (var authentication : Arrays.asList(null, anonymous, notYetChecked)) {
			assertThat(access.canSee(authentication, "LOGIN")).isTrue();
			assertThat(access.canSee(authentication, "TODO")).isFalse();
		}
		verify(users, never()).canSee("david", "LOGIN");
	}

	@Test
	void findsTheScreensWhoseApiServesARequestAndReadsThemOnce() {
		given(screens.findAll()).willReturn(List.of(screen("LOGIN", 10, null, null, true),
				screen("TODO", 20, "/api/todos/**", null, false), screen("TODO_TOO", 30, "/api/todos/shared/**", null, false)));

		assertThat(access.screensServing(new MockHttpServletRequest("GET", "/api/todos/1"))).containsExactly("TODO");
		assertThat(access.screensServing(new MockHttpServletRequest("GET", "/api/todos/shared/1")))
			.containsExactly("TODO", "TODO_TOO");
		assertThat(access.screensServing(new MockHttpServletRequest("GET", "/api/auth/me"))).isEmpty();
		verify(screens, times(1)).findAll();
	}

	@Test
	void listsTheScreensOfARoleInOrder() {
		given(roleScreens.findByRoleInScreenOrder(Role.USER))
			.willReturn(List.of(new RoleScreen(Role.USER, "TODO"), new RoleScreen(Role.USER, "ADMINISTRATION")));

		assertThat(access.screensOf(Role.USER)).containsExactly("TODO", "ADMINISTRATION");
	}

	@Test
	void describesVisitorsWhoAreNotSignedIn() {
		given(roles.findById(Role.ANONYMOUS)).willReturn(Optional.of(new Role(Role.ANONYMOUS, "Not logged")));
		given(roleScreens.findByRoleInScreenOrder(Role.ANONYMOUS)).willReturn(List.of(new RoleScreen(Role.ANONYMOUS, "LOGIN")));

		assertThat(access.anonymous()).isEqualTo(new Visitor(Role.ANONYMOUS, "Not logged", List.of("LOGIN")));
	}

	@Test
	void namesVisitorsByTheRoleCodeWhenTheRoleIsMissing() {
		given(roles.findById(Role.ANONYMOUS)).willReturn(Optional.empty());

		assertThat(access.anonymous()).isEqualTo(new Visitor(Role.ANONYMOUS, Role.ANONYMOUS, List.of()));
	}

}
