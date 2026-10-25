package com.example.backend.auth;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenRepository extends JpaRepository<Screen, String> {

	List<Screen> findAllByOrderBySortOrderAsc();

}
