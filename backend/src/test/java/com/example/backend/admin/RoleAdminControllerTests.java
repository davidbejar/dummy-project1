package com.example.backend.admin;

import static com.example.backend.testsupport.Entities.locked;
import static com.example.backend.testsupport.Entities.screen;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.backend.admin.RoleAdminController.CreateRoleRequest;
import com.example.backend.admin.RoleAdminController.RoleView;
import com.example.backend.admin.RoleAdminController.ScreenView;
import com.example.backend.auth.AppUserRepository;
import com.example.backend.auth.Role;
import com.example.backend.auth.RoleRepository;
import com.example.backend.auth.RoleScreen;
import com.example.backend.auth.RoleScreenRepository;
import com.example.backend.auth.Screen;
import com.example.backend.auth.ScreenRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/**
 * Role administration without Spring or a database. Assignments live in an in-memory list
 * behind the repository mock, so the tests see what the controller left behind.
 */
class RoleAdminControllerTests {

	/**
	 * Screens like the real ones, plus AUDIT below ROLE_ADMIN to check that the hierarchy is
	 * followed at any depth.
	 */
	private static final List<Screen> SCREENS = List.of(
			screen("LOGIN", 10, null, null, true),
			screen("TODO", 20, "/api/todos/**", null, false),
			screen("ADMINISTRATION", 30, null, null, false),
			screen("USER_ADMIN", 40, "/api/admin/users/**", "ADMINISTRATION", false),
			screen("ROLE_ADMIN", 50, "/api/admin/roles/**", "ADMINISTRATION", false),
			screen("AUDIT", 60, "/api/admin/audit/**", "ROLE_ADMIN", false));

	private static final Map<String, Integer> SORT_ORDER = SCREENS.stream()
		.collect(Collectors.toMap(Screen::getCode, Screen::getSortOrder));

	private final RoleRepository roles = mock(RoleRepository.class);

	private final ScreenRepository screens = mock(ScreenRepository.class);

	private final RoleScreenRepository roleScreens = mock(RoleScreenRepository.class);

	private final AppUserRepository users = mock(AppUserRepository.class);

	private final RoleAdminController controller = new RoleAdminController(roles, screens, roleScreens, users);

	private final List<RoleScreen> assignments = new ArrayList<>();

	private final Role tree = new Role("TREE", "Tree");

	@BeforeEach
	void fakeTheDatabase() {
		given(screens.findAll()).willReturn(SCREENS);
		given(screens.findAllByOrderBySortOrderAsc()).willReturn(SCREENS);
		given(roles.findById(anyString())).willReturn(Optional.empty());
		given(roles.findById("TREE")).willReturn(Optional.of(tree));
		given(roleScreens.findByRoleCodeAndScreenCode(anyString(), anyString())).willAnswer(
				inv -> assignments.stream().filter(matches(inv.getArgument(0), inv.getArgument(1))).findFirst());
		given(roleScreens.existsByRoleCodeAndScreenCode(anyString(), anyString())).willAnswer(
				inv -> assignments.stream().anyMatch(matches(inv.getArgument(0), inv.getArgument(1))));
		given(roleScreens.save(any(RoleScreen.class))).willAnswer(inv -> {
			assignments.add(inv.getArgument(0));
			return inv.getArgument(0);
		});
		willAnswer(inv -> assignments.removeAll(inv.<Collection<RoleScreen>>getArgument(0)))
			.given(roleScreens)
			.deleteAll(any());
		given(roleScreens.findByRoleInScreenOrder(anyString())).willAnswer(inv -> inScreenOrder(
				assignments.stream().filter(a -> a.getRoleCode().equals(inv.getArgument(0))).toList()));
		given(roleScreens.findAllInScreenOrder()).willAnswer(inv -> inScreenOrder(assignments));
	}

	@Test
	void offersEveryScreenButLoginWithItsParent() {
		assertThat(controller.screens()).containsExactly(
				new ScreenView("TODO", "Todo", null),
				new ScreenView("ADMINISTRATION", "Administration", null),
				new ScreenView("USER_ADMIN", "User_admin", "ADMINISTRATION"),
				new ScreenView("ROLE_ADMIN", "Role_admin", "ADMINISTRATION"),
				new ScreenView("AUDIT", "Audit", "ROLE_ADMIN"));
	}

	@Test
	void listsRolesWithTheirScreensLocksAndWhetherTheyCanBeDeleted() {
		given(roles.findAll(Sort.by("code"))).willReturn(List.of(new Role(Role.ADMIN, "Administrator"),
				new Role(Role.ANONYMOUS, "Not logged"), tree, new Role("EMPTY", "Empty")));
		assignments.addAll(List.of(locked(Role.ADMIN, "ROLE_ADMIN"), locked(Role.ADMIN, "TODO"),
				locked(Role.ANONYMOUS, "LOGIN"), new RoleScreen(Role.ANONYMOUS, "TODO"), new RoleScreen("TREE", "TODO")));

		Map<String, RoleView> byCode = controller.list()
			.stream()
			.collect(Collectors.toMap(RoleView::code, Function.identity()));

		assertThat(byCode.get(Role.ADMIN)).isEqualTo(new RoleView(Role.ADMIN, "Administrator",
				List.of("TODO", "ROLE_ADMIN"), List.of("TODO", "ROLE_ADMIN"), false));
		// Login is left out: it is not a screen roles can be given.
		assertThat(byCode.get(Role.ANONYMOUS)).isEqualTo(
				new RoleView(Role.ANONYMOUS, "Not logged", List.of("TODO"), List.of(), false));
		assertThat(byCode.get("TREE")).isEqualTo(new RoleView("TREE", "Tree", List.of("TODO"), List.of(), true));
		assertThat(byCode.get("EMPTY")).isEqualTo(new RoleView("EMPTY", "Empty", List.of(), List.of(), true));
		assertThat(controller.list()).extracting(RoleView::code).containsExactly(Role.ADMIN, Role.ANONYMOUS, "TREE", "EMPTY");
	}

	@Test
	void createsARoleWithAnUppercaseCodeAndTrimmedName() {
		given(roles.save(any(Role.class))).willAnswer(inv -> inv.getArgument(0));

		RoleView created = controller.create(new CreateRoleRequest(" mini_Admin ", "  Mini admin "));

		assertThat(created).isEqualTo(new RoleView("MINI_ADMIN", "Mini admin", List.of(), List.of(), true));
	}

	@Test
	void refusesARoleThatAlreadyExists() {
		given(roles.existsById("TREE")).willReturn(true);

		assertStatus(() -> controller.create(new CreateRoleRequest("tree", "Again")), HttpStatus.CONFLICT,
				"Role TREE already exists");
		verify(roles, never()).save(any());
	}

	@Test
	void deletesAnUnusedRole() {
		assertThat(controller.delete("TREE").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(roles).delete(tree);
	}

	@Test
	void answersNotFoundWhenDeletingAnUnknownRole() {
		assertThat(controller.delete("NOPE").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		verify(roles, never()).delete(any());
	}

	@Test
	void neverDeletesAdminOrAnonymous() {
		for (String code : List.of(Role.ADMIN, Role.ANONYMOUS)) {
			given(roles.findById(code)).willReturn(Optional.of(new Role(code, code)));

			assertStatus(() -> controller.delete(code), HttpStatus.CONFLICT, "Role " + code + " cannot be deleted");
		}
		verify(roles, never()).delete(any());
	}

	@Test
	void refusesToDeleteARoleStillGivenToUsers() {
		given(users.existsByRoleCode("TREE")).willReturn(true);

		assertStatus(() -> controller.delete("TREE"), HttpStatus.CONFLICT,
				"Role TREE is still given to users; delete them first");
		verify(roles, never()).delete(any());
	}

	@Test
	void givingAScreenGivesEveryScreenAboveIt() {
		RoleView view = controller.assign("TREE", "AUDIT");

		assertThat(view.screens()).containsExactly("ADMINISTRATION", "ROLE_ADMIN", "AUDIT");
		assertThat(view.lockedScreens()).isEmpty();
	}

	@Test
	void givingAScreenKeepsTheScreensAboveItThatTheRoleAlreadyHas() {
		assignments.add(new RoleScreen("TREE", "ADMINISTRATION"));

		assertThat(controller.assign("TREE", "USER_ADMIN").screens()).containsExactly("ADMINISTRATION", "USER_ADMIN");
		assertThat(assignments).hasSize(2);
	}

	@Test
	void givingAScreenTheRoleAlreadyHasChangesNothing() {
		assignments.add(new RoleScreen("TREE", "TODO"));

		assertThat(controller.assign("TREE", "TODO").screens()).containsExactly("TODO");
		verify(roleScreens, never()).save(any());
	}

	@Test
	void takingAScreenAwayTakesEveryScreenBelowIt() {
		assignments.addAll(List.of(new RoleScreen("TREE", "TODO"), new RoleScreen("TREE", "ADMINISTRATION"),
				new RoleScreen("TREE", "USER_ADMIN"), new RoleScreen("TREE", "ROLE_ADMIN"), new RoleScreen("TREE", "AUDIT"),
				new RoleScreen("OTHER", "ROLE_ADMIN")));

		assertThat(controller.unassign("TREE", "ROLE_ADMIN").screens()).containsExactly("TODO", "ADMINISTRATION", "USER_ADMIN");
		assertThat(controller.unassign("TREE", "ADMINISTRATION").screens()).containsExactly("TODO");
		// Other roles keep their screens.
		assertThat(assignments).extracting(RoleScreen::getRoleCode).containsExactly("TREE", "OTHER");
	}

	@Test
	void takingAwayAScreenTheRoleLacksChangesNothing() {
		assertThat(controller.unassign("TREE", "TODO").screens()).isEmpty();
	}

	@Test
	void lockedScreensCannotBeGivenOrTakenAway() {
		assignments.add(locked("TREE", "TODO"));

		assertStatus(() -> controller.assign("TREE", "TODO"), HttpStatus.CONFLICT,
				"Screen TODO of role TREE cannot be changed");
		assertStatus(() -> controller.unassign("TREE", "TODO"), HttpStatus.CONFLICT,
				"Screen TODO of role TREE cannot be changed");
		assertThat(assignments).hasSize(1);
	}

	@Test
	void aLockedScreenBelowKeepsItsParentTooAndNothingIsTakenAway() {
		assignments.addAll(List.of(new RoleScreen("TREE", "ADMINISTRATION"), new RoleScreen("TREE", "USER_ADMIN"),
				locked("TREE", "ROLE_ADMIN")));

		assertStatus(() -> controller.unassign("TREE", "ADMINISTRATION"), HttpStatus.CONFLICT,
				"Screen ROLE_ADMIN of role TREE cannot be changed");
		assertThat(assignments).hasSize(3);
		verify(roleScreens, never()).deleteAll(any());
	}

	@Test
	void loginIsOnlyForVisitorsWhoAreNotSignedIn() {
		for (String role : List.of("TREE", Role.ANONYMOUS)) {
			assertStatus(() -> controller.assign(role, "LOGIN"), HttpStatus.CONFLICT,
					"Screen LOGIN is only for visitors who are not signed in");
			assertStatus(() -> controller.unassign(role, "LOGIN"), HttpStatus.CONFLICT,
					"Screen LOGIN is only for visitors who are not signed in");
		}
		assertThat(assignments).isEmpty();
	}

	@Test
	void answersNotFoundForUnknownScreensAndRoles() {
		assertStatus(() -> controller.assign("TREE", "NOPE"), HttpStatus.NOT_FOUND, "Unknown screen NOPE");
		assertStatus(() -> controller.unassign("TREE", "NOPE"), HttpStatus.NOT_FOUND, "Unknown screen NOPE");
		assertStatus(() -> controller.assign("NOPE", "TODO"), HttpStatus.NOT_FOUND, "Unknown role NOPE");
		assertStatus(() -> controller.unassign("NOPE", "TODO"), HttpStatus.NOT_FOUND, "Unknown role NOPE");
		assertThat(assignments).isEmpty();
	}

	@Test
	void theRoleViewLeavesOutLoginAfterAChange() {
		Role anonymous = new Role(Role.ANONYMOUS, "Not logged");
		given(roles.findById(Role.ANONYMOUS)).willReturn(Optional.of(anonymous));
		assignments.add(locked(Role.ANONYMOUS, "LOGIN"));

		RoleView view = controller.assign(Role.ANONYMOUS, "TODO");

		assertThat(view).isEqualTo(new RoleView(Role.ANONYMOUS, "Not logged", List.of("TODO"), List.of(), false));
	}

	private static Predicate<RoleScreen> matches(String role, String screen) {
		return a -> a.getRoleCode().equals(role) && a.getScreenCode().equals(screen);
	}

	private static List<RoleScreen> inScreenOrder(List<RoleScreen> assignments) {
		return assignments.stream().sorted(Comparator.comparing(a -> SORT_ORDER.get(a.getScreenCode()))).toList();
	}

	private static void assertStatus(ThrowingCallable call, HttpStatusCode status,
			String reason) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
			assertThat(e.getStatusCode()).isEqualTo(status);
			assertThat(e.getReason()).isEqualTo(reason);
		});
	}

}
