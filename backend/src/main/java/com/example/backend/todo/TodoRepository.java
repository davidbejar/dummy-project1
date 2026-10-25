package com.example.backend.todo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<Todo, UUID> {

	List<Todo> findAllByOrderByCreatedAtAsc();

}
