package com.example.backend.todo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

	private final TodoRepository repository;

	public TodoController(TodoRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	public List<Todo> list() {
		return repository.findAllByOrderByCreatedAtAsc();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Todo create(@Valid @RequestBody CreateTodoRequest request) {
		return repository.save(new Todo(request.title().strip()));
	}

	@PatchMapping("/{id}")
	public ResponseEntity<Todo> update(@PathVariable UUID id, @Valid @RequestBody UpdateTodoRequest request) {
		return repository.findById(id).map(todo -> {
			if (request.title() != null) {
				todo.setTitle(request.title().strip());
			}
			if (request.completed() != null) {
				todo.setCompleted(request.completed());
			}
			return ResponseEntity.ok(repository.save(todo));
		}).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		if (!repository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		repository.deleteById(id);
		return ResponseEntity.noContent().build();
	}

	static final String TITLE_BLANK_MESSAGE = "Title must not be blank";

	static final String TITLE_TOO_LONG_MESSAGE = "Title must be at most " + Todo.TITLE_MAX_LENGTH + " characters";

	public record CreateTodoRequest(
			@NotBlank(message = TITLE_BLANK_MESSAGE) @Size(max = Todo.TITLE_MAX_LENGTH, message = TITLE_TOO_LONG_MESSAGE) String title) {
	}

	/** All fields optional; a title, when present, follows the same rules as on create. */
	public record UpdateTodoRequest(
			@Pattern(regexp = "(?s).*\\S.*", message = TITLE_BLANK_MESSAGE) @Size(max = Todo.TITLE_MAX_LENGTH, message = TITLE_TOO_LONG_MESSAGE) String title,
			Boolean completed) {
	}

}
