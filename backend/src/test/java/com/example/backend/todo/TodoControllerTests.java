package com.example.backend.todo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TodoController.class)
class TodoControllerTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private TodoRepository repository;

	@Test
	void listsTodos() throws Exception {
		given(repository.findAllByOrderByCreatedAtAsc()).willReturn(List.of(new Todo("Write tests")));

		mvc.perform(get("/api/todos"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].title").value("Write tests"));
	}

	@Test
	void rejectsBlankTitle() throws Exception {
		mvc.perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Title must not be blank"));
		verify(repository, never()).save(any());
	}

	@Test
	void rejectsTitleLongerThanMax() throws Exception {
		mvc.perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content(titleJson(Todo.TITLE_MAX_LENGTH + 1)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Title must be at most 255 characters"));
		verify(repository, never()).save(any());
	}

	@Test
	void acceptsTitleOfExactlyMaxLength() throws Exception {
		given(repository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));

		mvc.perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content(titleJson(Todo.TITLE_MAX_LENGTH)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("a".repeat(Todo.TITLE_MAX_LENGTH)));
	}

	@Test
	void stripsSurroundingWhitespaceOnCreate() throws Exception {
		given(repository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));

		mvc.perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  Ship it  \"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Ship it"));
	}

	@Test
	void rejectsBlankTitleOnUpdate() throws Exception {
		mvc.perform(patch("/api/todos/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Title must not be blank"));
		verify(repository, never()).save(any());
	}

	@Test
	void rejectsTooLongTitleOnUpdate() throws Exception {
		mvc.perform(patch("/api/todos/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(titleJson(Todo.TITLE_MAX_LENGTH + 1)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Title must be at most 255 characters"));
	}

	@Test
	void updateWithoutTitleOnlyChangesCompleted() throws Exception {
		Todo existing = new Todo("Keep me");
		UUID id = UUID.randomUUID();
		given(repository.findById(id)).willReturn(Optional.of(existing));
		given(repository.save(existing)).willReturn(existing);

		mvc.perform(patch("/api/todos/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Keep me"))
			.andExpect(jsonPath("$.completed").value(true));
	}

	private static String titleJson(int length) {
		return "{\"title\":\"" + "a".repeat(length) + "\"}";
	}

	@Test
	void createsTodo() throws Exception {
		given(repository.save(any(Todo.class))).willAnswer(inv -> inv.getArgument(0));

		mvc.perform(post("/api/todos").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Ship it\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Ship it"));
	}

}
