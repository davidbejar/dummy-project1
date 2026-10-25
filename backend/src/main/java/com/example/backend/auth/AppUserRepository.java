package com.example.backend.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByUsername(String username);

	boolean existsByUsername(String username);

	boolean existsByRoleCode(String roleCode);

	List<AppUser> findAllByOrderByUsernameAsc();

	/**
	 * Whether the user's current role may open the screen. Reads the database on every call,
	 * so changed assignments and deleted or disabled users take effect immediately.
	 */
	@Query("""
			select count(rs) > 0 from AppUser u, RoleScreen rs
			where rs.roleCode = u.role.code and u.username = :username and u.enabled = true
			and rs.screenCode = :screen""")
	boolean canSee(@Param("username") String username, @Param("screen") String screen);

}
