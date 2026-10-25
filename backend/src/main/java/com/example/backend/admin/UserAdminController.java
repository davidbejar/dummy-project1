package com.example.backend.admin;

import com.example.backend.auth.AppUser;
import com.example.backend.auth.AppUserDetails;
import com.example.backend.auth.AppUserRepository;
import com.example.backend.auth.Role;
import com.example.backend.auth.RoleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The user administration screen: list, add and delete users. */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

	/** Users that can never be deleted. */
	static final Set<String> PROTECTED_USERNAMES = Set.of("admin", "david");

	/** bcrypt only looks at the first 72 bytes of a password. */
	static final int PASSWORD_MAX_LENGTH = 72;

	private final AppUserRepository users;

	private final RoleRepository roles;

	private final PasswordEncoder passwordEncoder;

	public UserAdminController(AppUserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.roles = roles;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping
	public List<UserView> list() {
		return users.findAllByOrderByUsernameAsc().stream().map(UserView::from).toList();
	}

	/** The roles a user can be given: every role except ANONYMOUS. */
	@GetMapping("/roles")
	public List<RoleOption> roles() {
		return roles.findAll(Sort.by("code"))
			.stream()
			.filter(role -> !role.getCode().equals(Role.ANONYMOUS))
			.map(role -> new RoleOption(role.getCode(), role.getDisplayName()))
			.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public UserView create(@Valid @RequestBody CreateUserRequest request) {
		String username = request.username().strip().toLowerCase(Locale.ROOT);
		if (users.existsByUsername(username)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "User " + username + " already exists");
		}
		Role role = roles.findById(request.role())
			.filter(r -> !r.getCode().equals(Role.ANONYMOUS))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role " + request.role()));
		AppUser user = new AppUser(username, passwordEncoder.encode(request.password()), request.displayName().strip(),
				role);
		return UserView.from(users.save(user));
	}

	@DeleteMapping("/{id}")
	@Transactional
	public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me) {
		AppUser user = users.findById(id).orElse(null);
		if (user == null) {
			return ResponseEntity.notFound().build();
		}
		if (PROTECTED_USERNAMES.contains(user.getUsername())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "User " + user.getUsername() + " cannot be deleted");
		}
		if (me != null && user.getId().equals(me.id())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot delete yourself");
		}
		users.delete(user);
		return ResponseEntity.noContent().build();
	}

	public record UserView(UUID id, String username, String displayName, String role, String roleDisplayName,
			boolean deletable) {

		static UserView from(AppUser user) {
			return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().getCode(),
					user.getRole().getDisplayName(), !PROTECTED_USERNAMES.contains(user.getUsername()));
		}

	}

	public record RoleOption(String code, String displayName) {
	}

	public record CreateUserRequest(
			@NotBlank(message = "Username must not be blank") @Size(max = 50, message = "Username must be at most 50 characters") @Pattern(regexp = "\\s*[A-Za-z0-9._-]*\\s*", message = "Username may only contain letters, digits, dots, dashes and underscores") String username,
			@NotBlank(message = "Display name must not be blank") @Size(max = 100, message = "Display name must be at most 100 characters") String displayName,
			@NotBlank(message = "Password must not be blank") @Size(max = PASSWORD_MAX_LENGTH, message = "Password must be at most " + PASSWORD_MAX_LENGTH + " characters") String password,
			@NotBlank(message = "Role must be chosen") String role) {
	}

}
