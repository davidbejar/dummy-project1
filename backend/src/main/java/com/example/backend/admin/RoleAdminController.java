package com.example.backend.admin;

import com.example.backend.auth.AppUserRepository;
import com.example.backend.auth.Role;
import com.example.backend.auth.RoleRepository;
import com.example.backend.auth.RoleScreen;
import com.example.backend.auth.RoleScreenRepository;
import com.example.backend.auth.Screen;
import com.example.backend.auth.ScreenRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The role administration screen: list, add and delete roles, and choose each role's screens.
 * Screens only for visitors who are not signed in (login) are left out. A role given a screen
 * also gets the screens above it, and a role losing a screen also loses the screens below it.
 */
@RestController
@RequestMapping("/api/admin/roles")
public class RoleAdminController {

	/** Roles the application depends on, so they can never be deleted. */
	static final Set<String> PROTECTED_ROLES = Set.of(Role.ADMIN, Role.ANONYMOUS);

	private final RoleRepository roles;

	private final ScreenRepository screens;

	private final RoleScreenRepository roleScreens;

	private final AppUserRepository users;

	public RoleAdminController(RoleRepository roles, ScreenRepository screens, RoleScreenRepository roleScreens,
			AppUserRepository users) {
		this.roles = roles;
		this.screens = screens;
		this.roleScreens = roleScreens;
		this.users = users;
	}

	/** The screens roles can be given, in menu order: the columns of the role administration screen. */
	@GetMapping("/screens")
	public List<ScreenView> screens() {
		return screens.findAllByOrderBySortOrderAsc()
			.stream()
			.filter(screen -> !screen.isAnonymousOnly())
			.map(screen -> new ScreenView(screen.getCode(), screen.getDisplayName(), screen.getParentCode()))
			.toList();
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<RoleView> list() {
		Map<String, Screen> byCode = screensByCode();
		Map<String, List<RoleScreen>> assignments = roleScreens.findAllInScreenOrder()
			.stream()
			.filter(assignment -> isAssignable(byCode.get(assignment.getScreenCode())))
			.collect(Collectors.groupingBy(RoleScreen::getRoleCode));
		return roles.findAll(Sort.by("code"))
			.stream()
			.map(role -> RoleView.from(role, assignments.getOrDefault(role.getCode(), List.of())))
			.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public RoleView create(@Valid @RequestBody CreateRoleRequest request) {
		String code = request.code().strip().toUpperCase(Locale.ROOT);
		if (roles.existsById(code)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Role " + code + " already exists");
		}
		return RoleView.from(roles.save(new Role(code, request.displayName().strip())), List.of());
	}

	@DeleteMapping("/{code}")
	@Transactional
	public ResponseEntity<Void> delete(@PathVariable String code) {
		Role role = roles.findById(code).orElse(null);
		if (role == null) {
			return ResponseEntity.notFound().build();
		}
		if (PROTECTED_ROLES.contains(code)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Role " + code + " cannot be deleted");
		}
		if (users.existsByRoleCode(code)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Role " + code + " is still given to users; delete them first");
		}
		roles.delete(role); // the database deletes its app_role_screen rows (ON DELETE CASCADE)
		return ResponseEntity.noContent().build();
	}

	/** Lets the role open the screen, and the screens above it through which it is reached. */
	@PutMapping("/{code}/screens/{screen}")
	@Transactional
	public RoleView assign(@PathVariable String code, @PathVariable String screen) {
		Map<String, Screen> byCode = screensByCode();
		Role role = findRoleAndScreen(code, screen, byCode);
		RoleScreen existing = roleScreens.findByRoleCodeAndScreenCode(code, screen).orElse(null);
		if (existing != null && existing.isLocked()) {
			throw locked(code, screen);
		}
		for (Screen s = byCode.get(screen); s != null; s = byCode.get(s.getParentCode())) {
			if (!roleScreens.existsByRoleCodeAndScreenCode(code, s.getCode())) {
				roleScreens.save(new RoleScreen(code, s.getCode()));
			}
		}
		return view(role, byCode);
	}

	/** Stops the role from opening the screen, and the screens below it. */
	@DeleteMapping("/{code}/screens/{screen}")
	@Transactional
	public RoleView unassign(@PathVariable String code, @PathVariable String screen) {
		Map<String, Screen> byCode = screensByCode();
		Role role = findRoleAndScreen(code, screen, byCode);
		List<RoleScreen> removed = new ArrayList<>();
		for (String s : withDescendants(screen, byCode)) {
			RoleScreen existing = roleScreens.findByRoleCodeAndScreenCode(code, s).orElse(null);
			if (existing != null && existing.isLocked()) {
				throw locked(code, s);
			}
			if (existing != null) {
				removed.add(existing);
			}
		}
		roleScreens.deleteAll(removed);
		return view(role, byCode);
	}

	private Role findRoleAndScreen(String code, String screen, Map<String, Screen> byCode) {
		if (!byCode.containsKey(screen)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown screen " + screen);
		}
		if (!isAssignable(byCode.get(screen))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Screen " + screen + " is only for visitors who are not signed in");
		}
		return roles.findById(code)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown role " + code));
	}

	private Map<String, Screen> screensByCode() {
		return screens.findAll().stream().collect(Collectors.toMap(Screen::getCode, Function.identity()));
	}

	private static boolean isAssignable(Screen screen) {
		return screen != null && !screen.isAnonymousOnly();
	}

	/** The screen and every screen below it, at any depth. */
	private static List<String> withDescendants(String screen, Map<String, Screen> byCode) {
		List<String> result = new ArrayList<>(List.of(screen));
		for (int i = 0; i < result.size(); i++) {
			String parent = result.get(i);
			byCode.values().stream().filter(s -> parent.equals(s.getParentCode())).map(Screen::getCode).forEach(result::add);
		}
		return result;
	}

	private static ResponseStatusException locked(String code, String screen) {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Screen " + screen + " of role " + code + " cannot be changed");
	}

	private RoleView view(Role role, Map<String, Screen> byCode) {
		roleScreens.flush();
		return RoleView.from(role, roleScreens.findByRoleInScreenOrder(role.getCode())
			.stream()
			.filter(assignment -> isAssignable(byCode.get(assignment.getScreenCode())))
			.toList());
	}

	/** A screen roles can be given; {@code parentCode} is the screen it is reached through, if any. */
	public record ScreenView(String code, String displayName, String parentCode) {
	}

	/**
	 * A role with the codes of its screens. {@code lockedScreens} are the assignments that
	 * cannot be removed ({@code app_role_screen.locked}), e.g. all of ADMIN's. Screens only for
	 * visitors who are not signed in are left out.
	 */
	public record RoleView(String code, String displayName, List<String> screens, List<String> lockedScreens,
			boolean deletable) {

		static RoleView from(Role role, List<RoleScreen> assignments) {
			return new RoleView(role.getCode(), role.getDisplayName(),
					assignments.stream().map(RoleScreen::getScreenCode).toList(),
					assignments.stream().filter(RoleScreen::isLocked).map(RoleScreen::getScreenCode).toList(),
					!PROTECTED_ROLES.contains(role.getCode()));
		}

	}

	public record CreateRoleRequest(
			@NotBlank(message = "Code must not be blank") @Size(max = 20, message = "Code must be at most 20 characters") @Pattern(regexp = "\\s*([A-Za-z][A-Za-z0-9_]*)?\\s*", message = "Code must start with a letter and contain only letters, digits and underscores") String code,
			@NotBlank(message = "Display name must not be blank") @Size(max = 100, message = "Display name must be at most 100 characters") String displayName) {
	}

}
