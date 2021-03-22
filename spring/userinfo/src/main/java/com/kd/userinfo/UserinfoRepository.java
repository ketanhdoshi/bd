package com.kd.userinfo;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

// This interface will be Auto-Implemented by Spring into a Bean called userinfoRepository
public interface UserinfoRepository extends ReactiveCrudRepository<Userinfo, Long> {
    /**
	 * Find a Userinfo with the given username.
	 *
	 * @param username
	 * @return The user if found, null otherwise.
	 */
	public Mono<Userinfo> findByUsername(String username);
}
