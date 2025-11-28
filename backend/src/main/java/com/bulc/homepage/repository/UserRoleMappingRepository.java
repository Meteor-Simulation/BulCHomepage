package com.bulc.homepage.repository;

import com.bulc.homepage.entity.UserRoleMapping;
import com.bulc.homepage.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleMappingRepository extends JpaRepository<UserRoleMapping, Long> {

    List<UserRoleMapping> findByUser(User user);

    @Query("SELECT urm FROM UserRoleMapping urm JOIN FETCH urm.role WHERE urm.user.id = :userId")
    List<UserRoleMapping> findByUserIdWithRole(@Param("userId") Long userId);
}
