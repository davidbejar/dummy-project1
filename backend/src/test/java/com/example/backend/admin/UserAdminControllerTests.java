package com.example.backend.admin;

import static com.example.backend.testsupport.Entities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.backend.admin.UserAdminController.CreateUserRequest;
import com.example.backend.admin.UserAdminController.RoleOption;
import com.example.backend.admin.UserAdminController.UserView;
import com.example.backend.auth.AppUser;
import com.example.backend.auth.AppUserDetails;
import com.example.backend.auth.AppUserRepository;
import com.example.backend.auth.Role;
import com.example.backend.auth.RoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/** User administration without Spring or a database. */
class UserAdminControllerTests {

	private final AppUserRepository users = mock(AppUserRepository.class);

	private final RoleRepository roles = mock(RoleRepository.class);

	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

	private final UserAdminController controller = new UserAdminController(users, roles, passwordEncoder);

	private final Role userRole = new Role(Role.USER, "User");

	@Test
	void listsUsersAndWhetherTheyCanBeDeleted() {
		AppUser admin = user("admin", new Role(Role.ADMIN, "Administrator"));
		AppUser temp = user("temp", userRole);
		given(users.findAllByOrderByUsernameAsc()).willReturn(List.of(admin, user("david", userRole), temp));

		assertThat(controller.list()).containsExactly(
				new UserView(admin.getId(), "admin", "Name of admin", Role.ADMIN, "Administrator", false),
				new UserView(user("david", userRole).getId(), "david", "Name of david", Role.USER, "User", false),
				new UserView(temp.getId(), "temp", "Name of temp", Role.USER, "User", true));
	}

	@Test
	void offersEveryRoleButAnonymous() {
		given(roles.findAll(Sort.by("code"))).willReturn(
				List.of(new Role(Role.ADMIN, "Administrator"), new Role(Role.ANONYMOUS, "Not logged"), userRole));

		assertThat(controller.roles()).containsExactly(new RoleOption(Role.ADMIN, "Administrator"),
				new RoleOption(Role.USER, "User"));
	}

	@Test
	void createsAUserWithALowercaseNameAndAHashedPassword() {
		given(roles.findById(Role.USER)).willReturn(Optional.of(userRole));
		given(passwordEncoder.encode("s3cret")).willReturn("hashed");
		given(users.save(any(AppUser.class))).willAnswer(inv -> inv.getArgument(0));

		UserView created = controller.create(new CreateUserRequest("  New.Bie ", " New Bie  ", "s3cret", Role.USER));

		assertThat(created).isEqualTo(new UserView(null, "new.bie", "New Bie", Role.USER, "User", true));
		verify(users).save(argThat(u -> u.getPasswordHash().equals("hashed")));
	}

	@Test
	void refusesAUsernameThatIsTaken() {
		given(users.existsByUsername("temp")).willReturn(true);

		assertStatus(() -> controller.create(new CreateUserRequest("Temp", "Temp", "pw", Role.USER)), HttpStatus.CONFLICT,
				"User temp already exists");
		verify(users, never()).save(any());
	}

	@Test
	void refusesUnknownRolesAndAnonymous() {
		given(roles.findById(Role.ANONYMOUS)).willReturn(Optional.of(new Role(Role.ANONYMOUS, "Not logged")));

		for (String role : List.of("NOPE", Role.ANONYMOUS)) {
			assertStatus(() -> controller.create(new CreateUserRequest("temp", "Temp", "pw", role)), HttpStatus.BAD_REQUEST,
					"Unknown role " + role);
		}
		verify(users, never()).save(any());
	}

	@Test
	void deletesAUser() {
		AppUser temp = user("temp", userRole);
		given(users.findById(temp.getId())).willReturn(Optional.of(temp));

		assertThat(controller.delete(temp.getId(), me("admin")).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(users).delete(temp);
	}

	@Test
	void deletesAUserWithoutKnowingWhoAsks() {
		AppUser temp = user("temp", userRole);
		given(users.findById(temp.getId())).willReturn(Optional.of(temp));

		assertThat(controller.delete(temp.getId(), null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(users).delete(temp);
	}

	@Test
	void answersNotFoundForAnUnknownUser() {
		assertThat(controller.delete(UUID.randomUUID(), me("admin")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		verify(users, never()).delete(any());
	}

	@Test
	void neverDeletesAdminOrDavid() {
		for (String username : List.of("admin", "david")) {
			AppUser user = user(username, userRole);
			given(users.findById(user.getId())).willReturn(Optional.of(user));

			assertStatus(() -> controller.delete(user.getId(), me("someone")), HttpStatus.CONFLICT,
					"User " + username + " cannot be deleted");
		}
		verify(users, never()).delete(any());
	}

	@Test
	void nobodyDeletesThemselves() {
		AppUser mini = user("mini", userRole);
		given(users.findById(mini.getId())).willReturn(Optional.of(mini));

		assertStatus(() -> controller.delete(mini.getId(), me("mini")), HttpStatus.CONFLICT, "You cannot delete yourself");
		verify(users, never()).delete(any());
	}

	/** The signed-in user, with the same id {@code Entities.user} gives that username. */
	private AppUserDetails me(String username) {
		return new AppUserDetails(user(username, userRole).getId(), username, "hash", username, Role.USER, "User", true);
	}

	private static void assertStatus(ThrowingCallable call, HttpStatus status, String reason) {
		assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
			assertThat(e.getStatusCode()).isEqualTo(status);
			assertThat(e.getReason()).isEqualTo(reason);
		});
	}

}
