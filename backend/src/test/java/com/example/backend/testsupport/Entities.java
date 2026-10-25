package com.example.backend.testsupport;

import com.example.backend.auth.AppUser;
import com.example.backend.auth.Role;
import com.example.backend.auth.RoleScreen;
import com.example.backend.auth.Screen;
import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Builds entities for unit tests the way the database would hand them out: with the fields JPA
 * sets (ids, screen rows, locks) that the application code itself never writes.
 */
public final class Entities {

	private Entities() {
	}

	public static Screen screen(String code, int sortOrder, String apiPath, String parentCode, boolean anonymousOnly) {
		Screen screen = BeanUtils.instantiateClass(Screen.class);
		ReflectionTestUtils.setField(screen, "code", code);
		ReflectionTestUtils.setField(screen, "displayName", code.charAt(0) + code.substring(1).toLowerCase());
		ReflectionTestUtils.setField(screen, "sortOrder", sortOrder);
		ReflectionTestUtils.setField(screen, "apiPath", apiPath);
		ReflectionTestUtils.setField(screen, "parentCode", parentCode);
		ReflectionTestUtils.setField(screen, "anonymousOnly", anonymousOnly);
		return screen;
	}

	public static RoleScreen locked(String role, String screen) {
		RoleScreen assignment = new RoleScreen(role, screen);
		ReflectionTestUtils.setField(assignment, "locked", true);
		return assignment;
	}

	public static AppUser user(String username, Role role) {
		AppUser user = new AppUser(username, "{noop}" + username, "Name of " + username, role);
		ReflectionTestUtils.setField(user, "id", UUID.nameUUIDFromBytes(username.getBytes()));
		return user;
	}

	public static AppUser disabledUser(String username, Role role) {
		AppUser user = user(username, role);
		ReflectionTestUtils.setField(user, "enabled", false);
		return user;
	}

}
