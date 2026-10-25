package com.example.backend.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleScreenRepository extends JpaRepository<RoleScreen, RoleScreen.Key> {

	Optional<RoleScreen> findByRoleCodeAndScreenCode(String roleCode, String screenCode);

	boolean existsByRoleCodeAndScreenCode(String roleCode, String screenCode);

	/** Every assignment, in screen order. */
	@Query("select rs from RoleScreen rs, Screen s where s.code = rs.screenCode order by s.sortOrder")
	List<RoleScreen> findAllInScreenOrder();

	/** A role's assignments, in screen order. */
	@Query("select rs from RoleScreen rs, Screen s where s.code = rs.screenCode and rs.roleCode = :role order by s.sortOrder")
	List<RoleScreen> findByRoleInScreenOrder(@Param("role") String role);

}
