package com.kd.kdspring.user;

import org.springframework.data.repository.CrudRepository;

// This interface will be Auto-Implemented by Spring into a Bean called userRepository
public interface UserRepository extends CrudRepository<UserInfo, Long> {
    /**
	 * Find a User with the given username.
	 *
	 * @param username
	 * @return The user if found, null otherwise.
	 */
	public UserInfo findByUsername(String username);
}
