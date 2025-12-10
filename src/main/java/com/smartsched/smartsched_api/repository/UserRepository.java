package com.smartsched.smartsched_api.repository;

import com.smartsched.smartsched_api.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);
    List<User> findByEnabled(boolean enabled);
    List<User> findByRoleAndEnabled(com.smartsched.smartsched_api.model.Role role, boolean enabled);
    Boolean existsByUsername(String username);
}
