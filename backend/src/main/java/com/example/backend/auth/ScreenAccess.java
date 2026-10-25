package com.example.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Decides who may open which screen, from the screens, roles and assignments in the database. */
@Service
public class ScreenAccess {

	private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

	private final AppUserRepository users;

	private final RoleRepository roles;

	private final ScreenRepository screens;

	private final RoleScreenRepository roleScreens;

	/**
	 * Which screen serves which API, read once. Screens only change through migrations, which
	 * run at startup; who may open them is read on every request.
	 */
	private volatile List<ScreenApi> screenApis;

	public ScreenAccess(AppUserRepository users, RoleRepository roles, ScreenRepository screens,
			RoleScreenRepository roleScreens) {
		this.users = users;
		this.roles = roles;
		this.screens = screens;
		this.roleScreens = roleScreens;
	}

	/** Signed-in users are checked through their role; everyone else has the ANONYMOUS role. */
	@Transactional(readOnly = true)
	public boolean canSee(Authentication authentication, String screen) {
		if (authentication == null || !authentication.isAuthenticated() || trustResolver.isAnonymous(authentication)) {
			return roleScreens.existsByRoleCodeAndScreenCode(Role.ANONYMOUS, screen);
		}
		return users.canSee(authentication.getName(), screen);
	}

	/** Codes of the screens whose API ({@code app_screen.api_path}) serves this request. */
	public List<String> screensServing(HttpServletRequest request) {
		List<ScreenApi> apis = screenApis;
		if (apis == null) {
			apis = screens.findAll()
				.stream()
				.filter(screen -> screen.getApiPath() != null)
				.map(screen -> new ScreenApi(screen.getCode(),
						PathPatternRequestMatcher.withDefaults().matcher(screen.getApiPath())))
				.toList();
			screenApis = apis;
		}
		return apis.stream().filter(api -> api.matcher().matches(request)).map(ScreenApi::screen).toList();
	}

	/** Codes of the screens a role may open, in menu order. */
	@Transactional(readOnly = true)
	public List<String> screensOf(String role) {
		return roleScreens.findByRoleInScreenOrder(role).stream().map(RoleScreen::getScreenCode).toList();
	}

	/** What a visitor who is not signed in may open. */
	@Transactional(readOnly = true)
	public Visitor anonymous() {
		String displayName = roles.findById(Role.ANONYMOUS).map(Role::getDisplayName).orElse(Role.ANONYMOUS);
		return new Visitor(Role.ANONYMOUS, displayName, screensOf(Role.ANONYMOUS));
	}

	private record ScreenApi(String screen, RequestMatcher matcher) {
	}

}
