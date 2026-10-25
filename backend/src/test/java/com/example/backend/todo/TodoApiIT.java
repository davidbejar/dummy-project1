package com.example.backend.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end test against a real CockroachDB (start it with {@code podman compose up -d}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TodoApiIT {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TodoRepository repository;

	@Test
	void connectsToCockroachDb() {
		assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("CockroachDB");
	}

	@Test
	void crudRoundTripPersistsToDatabase() throws Exception {
		String body = mvc.perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"IT check\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
		assertThat(repository.findById(id)).isPresent();

		mvc.perform(patch("/api/todos/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.completed").value(true));
		assertThat(repository.findById(id)).get().extracting(Todo::isCompleted).isEqualTo(true);

		mvc.perform(get("/api/todos")).andExpect(status().isOk());

		mvc.perform(delete("/api/todos/" + id)).andExpect(status().isNoContent());
		assertThat(repository.existsById(id)).isFalse();
	}

	/**
	 * The column types in CockroachDB must line up with the Java entity and the TypeScript
	 * {@code Todo} interface (UUID/string, BOOL/boolean, TIMESTAMPTZ/ISO string).
	 */
	@Test
	void schemaMatchesEntityTypes() {
		Map<String, String> types = jdbc
			.queryForList("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'todo'")
			.stream()
			.collect(Collectors.toMap(row -> (String) row.get("column_name"), row -> (String) row.get("data_type")));

		assertThat(types).containsExactlyInAnyOrderEntriesOf(Map.of(
				"id", "uuid",
				"title", "character varying",
				"completed", "boolean",
				"created_at", "timestamp with time zone"));
		assertThat(jdbc.queryForObject(
				"SELECT character_maximum_length FROM information_schema.columns WHERE table_name = 'todo' AND column_name = 'title'",
				Integer.class))
			.isEqualTo(Todo.TITLE_MAX_LENGTH);
	}

	@Test
	void maxLengthTitleRoundTripsThroughCockroachDb() throws Exception {
		String title = "x".repeat(Todo.TITLE_MAX_LENGTH);
		String body = mvc.perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID id = UUID.fromString(JsonPath.read(body, "$.id"));

		assertThat(repository.findById(id)).get().extracting(Todo::getTitle).isEqualTo(title);
		repository.deleteById(id);
	}

	@Test
	void tooLongTitleIsRejectedBeforeReachingDatabase() throws Exception {
		long before = repository.count();

		mvc.perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"" + "x".repeat(Todo.TITLE_MAX_LENGTH + 1) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Title must be at most 255 characters"));

		assertThat(repository.count()).isEqualTo(before);
	}

	/** The database is the last line of defence, even for writes that bypass the API. */
	@Test
	void databaseRejectsTooLongTitle() {
		assertThatThrownBy(() -> jdbc.update("INSERT INTO todo (title) VALUES (?)", "x".repeat(Todo.TITLE_MAX_LENGTH + 1)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsBlankTitle() {
		assertThatThrownBy(() -> jdbc.update("INSERT INTO todo (title) VALUES (?)", "   "))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("CHECK constraint");
	}

}
