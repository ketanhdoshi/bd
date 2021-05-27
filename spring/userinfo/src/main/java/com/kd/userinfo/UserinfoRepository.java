package com.kd.userinfo;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

// ------------------------------------------
// Reactive Repository for User Info DB
// ------------------------------------------

// This interface will be Auto-Implemented by Spring into a Bean called userinfoRepository
// Here we simply define the Query methods for which we want implementations auto-generated.
public interface UserinfoRepository extends ReactiveCrudRepository<Userinfo, Long> {
    /**
	 * Find a Userinfo with the given username.
	 *
	 * @param username
	 * @return The user if found, null otherwise.
	 */
	public Mono<Userinfo> findByUsername(String username);
}
