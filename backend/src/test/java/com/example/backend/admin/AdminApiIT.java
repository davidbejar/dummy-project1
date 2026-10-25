package com.example.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.testsupport.Browser;
import com.example.backend.testsupport.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Screens, roles and users against CockroachDB, through the real login: what each role may
 * open, and the administration screens that change it.
 */
@SpringBootTest
@IntegrationTest
@AutoConfigureMockMvc
class AdminApiIT {

	private static final String NOT_ENOUGH_PERMISSIONS = "Not enough permissions";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private Browser admin;

	@BeforeEach
	void signInAsAdmin() throws Exception {
		removeTestData();
		admin = new Browser(mvc);
		admin.login("admin", "admin").andExpect(status().isOk());
	}

	@AfterEach
	void removeTestData() {
		jdbc.update("DELETE FROM app_user WHERE username LIKE 'it-%'");
		jdbc.update("DELETE FROM app_role WHERE code LIKE 'IT_%'");
	}

	@Test
	void seededScreensAndAssignments() throws Exception {
		assertThat(jdbc.queryForList("SELECT code FROM app_screen ORDER BY sort_order", String.class))
			.containsExactly("LOGIN", "TODO", "ADMINISTRATION", "USER_ADMIN", "ROLE_ADMIN");
		assertThat(jdbc.queryForList("SELECT screen_code FROM app_role_screen WHERE locked ORDER BY role_code, screen_code", String.class))
			.containsExactly("ADMINISTRATION", "ROLE_ADMIN", "TODO", "USER_ADMIN", "LOGIN");
		// Login belongs to visitors who are not signed in, so roles cannot be given it.
		admin.send(get("/api/admin/roles/screens"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code").value(contains("TODO", "ADMINISTRATION", "USER_ADMIN", "ROLE_ADMIN")))
			.andExpect(jsonPath("$[?(@.code == 'ROLE_ADMIN')].parentCode").value(contains("ADMINISTRATION")));

		mvc.perform(get("/api/auth/anonymous"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.roleDisplayName").value("Not logged"))
			.andExpect(jsonPath("$.screens").value(contains("LOGIN")));
		admin.send(get("/api/auth/me"))
			.andExpect(jsonPath("$.screens").value(contains("TODO", "ADMINISTRATION", "USER_ADMIN", "ROLE_ADMIN")));
		Browser david = new Browser(mvc);
		david.login("david", "david").andExpect(jsonPath("$.screens").value(contains("TODO")));
	}

	@Test
	void regularUsersCannotUseTheAdministrationApi() throws Exception {
		Browser david = new Browser(mvc);
		david.login("david", "david").andExpect(status().isOk());

		david.send(get("/api/admin/users"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value(NOT_ENOUGH_PERMISSIONS));
		david.send(get("/api/admin/roles")).andExpect(status().isForbidden());
		david.send(delete("/api/admin/users/" + userId("admin"))).andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
	}

	/** The example from the requirements: a role that sees Todos and users, but not roles. */
	@Test
	void miniAdminSeesOnlyTheScreensOfItsRole() throws Exception {
		admin.send(json(post("/api/admin/roles"), "{\"code\":\"it_mini\",\"displayName\":\"Mini admin\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("IT_MINI"))
			.andExpect(jsonPath("$.deletable").value(true));
		for (String screen : List.of("TODO", "ADMINISTRATION", "USER_ADMIN")) {
			admin.send(put("/api/admin/roles/IT_MINI/screens/" + screen)).andExpect(status().isOk());
		}
		admin.send(json(post("/api/admin/users"),
				"{\"username\":\"It-Mini\",\"displayName\":\"Mini\",\"password\":\"s3cret\",\"role\":\"IT_MINI\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.username").value("it-mini"))
			.andExpect(jsonPath("$.roleDisplayName").value("Mini admin"))
			.andExpect(jsonPath("$.password").doesNotExist());

		Browser mini = new Browser(mvc);
		mini.login("it-mini", "s3cret")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.screens").value(contains("TODO", "ADMINISTRATION", "USER_ADMIN")));
		mini.send(get("/api/todos")).andExpect(status().isOk());
		mini.send(get("/api/admin/users")).andExpect(status().isOk()).andExpect(jsonPath("$[*].username").value(hasItem("david")));
		mini.send(get("/api/admin/users/roles"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code").value(not(hasItem("ANONYMOUS"))));
		mini.send(get("/api/admin/roles"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value(NOT_ENOUGH_PERMISSIONS));
		mini.send(get("/api/admin/roles/screens")).andExpect(status().isForbidden());
		mini.send(put("/api/admin/roles/IT_MINI/screens/ROLE_ADMIN")).andExpect(status().isForbidden());

		// Changes apply to running sessions at once.
		admin.send(delete("/api/admin/roles/IT_MINI/screens/TODO"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.screens").value(contains("ADMINISTRATION", "USER_ADMIN")));
		mini.send(get("/api/todos")).andExpect(status().isForbidden());
		mini.send(get("/api/auth/me")).andExpect(jsonPath("$.screens").value(contains("ADMINISTRATION", "USER_ADMIN")));

		mini.send(delete("/api/admin/users/" + userId("it-mini")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("You cannot delete yourself"));
	}

	@Test
	void screensComeAndGoWithTheScreensAboveAndBelowThem() throws Exception {
		admin.send(json(post("/api/admin/roles"), "{\"code\":\"IT_TREE\",\"displayName\":\"Tree\"}"))
			.andExpect(status().isCreated());

		// Role administration is reached through the administration menu, so it comes with it.
		admin.send(put("/api/admin/roles/IT_TREE/screens/ROLE_ADMIN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.screens").value(contains("ADMINISTRATION", "ROLE_ADMIN")));
		admin.send(put("/api/admin/roles/IT_TREE/screens/USER_ADMIN"))
			.andExpect(jsonPath("$.screens").value(contains("ADMINISTRATION", "USER_ADMIN", "ROLE_ADMIN")));
		admin.send(put("/api/admin/roles/IT_TREE/screens/TODO")).andExpect(status().isOk());

		// Removing a child leaves its parent; removing the parent removes every child.
		admin.send(delete("/api/admin/roles/IT_TREE/screens/USER_ADMIN"))
			.andExpect(jsonPath("$.screens").value(contains("TODO", "ADMINISTRATION", "ROLE_ADMIN")));
		admin.send(delete("/api/admin/roles/IT_TREE/screens/ADMINISTRATION"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.screens").value(contains("TODO")));

		// ADMIN's screens are locked, so its administration menu cannot be removed with them.
		admin.send(delete("/api/admin/roles/ADMIN/screens/ADMINISTRATION")).andExpect(status().isConflict());
	}

	@Test
	void addsAndDeletesUsers() throws Exception {
		admin.send(json(post("/api/admin/users"),
				"{\"username\":\"it-temp\",\"displayName\":\"Temp\",\"password\":\"pw\",\"role\":\"USER\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.deletable").value(true));
		new Browser(mvc).login("it-temp", "pw").andExpect(status().isOk());

		admin.send(json(post("/api/admin/users"),
				"{\"username\":\"it-temp\",\"displayName\":\"Again\",\"password\":\"pw\",\"role\":\"USER\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("User it-temp already exists"));

		admin.send(delete("/api/admin/users/" + userId("it-temp"))).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE username = 'it-temp'", Integer.class)).isZero();
	}

	@Test
	void newUsersNeedAPasswordAndARealRole() throws Exception {
		admin.send(json(post("/api/admin/users"),
				"{\"username\":\"it-nopw\",\"displayName\":\"No password\",\"password\":\"\",\"role\":\"USER\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Password must not be blank"));
		admin.send(json(post("/api/admin/users"),
				"{\"username\":\"it-anon\",\"displayName\":\"Anon\",\"password\":\"pw\",\"role\":\"ANONYMOUS\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Unknown role ANONYMOUS"));
	}

	@Test
	void adminAndDavidCannotBeDeleted() throws Exception {
		for (String username : List.of("admin", "david")) {
			admin.send(delete("/api/admin/users/" + userId(username)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("User " + username + " cannot be deleted"));
		}
		admin.send(get("/api/admin/users"))
			.andExpect(jsonPath("$[?(@.username == 'admin')].deletable").value(contains(false)))
			.andExpect(jsonPath("$[?(@.username == 'david')].deletable").value(contains(false)));
	}

	@Test
	void protectedRolesAndAssignmentsCannotChange() throws Exception {
		admin.send(delete("/api/admin/roles/ADMIN"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Role ADMIN cannot be deleted"));
		admin.send(delete("/api/admin/roles/ANONYMOUS")).andExpect(status().isConflict());
		admin.send(delete("/api/admin/roles/USER"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Role USER is still given to users; delete them first"));
		admin.send(delete("/api/admin/roles/ADMIN/screens/ROLE_ADMIN"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Screen ROLE_ADMIN of role ADMIN cannot be changed"));
		admin.send(delete("/api/admin/roles/ANONYMOUS/screens/LOGIN"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Screen LOGIN is only for visitors who are not signed in"));
		admin.send(put("/api/admin/roles/USER/screens/LOGIN")).andExpect(status().isConflict());

		admin.send(get("/api/admin/roles"))
			.andExpect(jsonPath("$[?(@.code == 'ADMIN')].deletable").value(contains(false)))
			.andExpect(jsonPath("$[?(@.code == 'ANONYMOUS')].screens[*]").isEmpty())
			.andExpect(jsonPath("$[?(@.code == 'USER')].screens[*]").value(contains("TODO")));
		admin.send(get("/api/auth/me")).andExpect(jsonPath("$.screens").value(hasItem("ROLE_ADMIN")));
	}

	@Test
	void deletesAnUnusedRoleWithItsAssignments() throws Exception {
		admin.send(json(post("/api/admin/roles"), "{\"code\":\"IT_GONE\",\"displayName\":\"Gone\"}")).andExpect(status().isCreated());
		admin.send(put("/api/admin/roles/IT_GONE/screens/TODO")).andExpect(status().isOk());
		admin.send(json(post("/api/admin/roles"), "{\"code\":\"IT_GONE\",\"displayName\":\"Again\"}"))
			.andExpect(status().isConflict());

		admin.send(delete("/api/admin/roles/IT_GONE")).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_role_screen WHERE role_code = 'IT_GONE'", Integer.class))
			.isZero();
	}

	@Test
	void anonymousVisitorsGetTheScreensOfTheAnonymousRole() throws Exception {
		mvc.perform(get("/api/todos")).andExpect(status().isUnauthorized());
		admin.send(put("/api/admin/roles/ANONYMOUS/screens/TODO")).andExpect(status().isOk());
		try {
			mvc.perform(get("/api/todos")).andExpect(status().isOk());
		}
		finally {
			admin.send(delete("/api/admin/roles/ANONYMOUS/screens/TODO")).andExpect(status().isOk());
		}
		mvc.perform(get("/api/todos")).andExpect(status().isUnauthorized());
	}

	private String userId(String username) {
		return jdbc.queryForObject("SELECT id::STRING FROM app_user WHERE username = ?", String.class, username);
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
