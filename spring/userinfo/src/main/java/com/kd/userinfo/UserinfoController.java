package com.kd.userinfo;

import javax.validation.Valid;
import java.util.logging.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
public class UserinfoController {

	protected Logger logger = Logger.getLogger(UserinfoController.class.getName());

	// Inject the Userinfo Repository
	@Autowired
	protected UserinfoRepository userinfoRepository;

	@Autowired
	private PasswordEncoder bcryptEncoder;

	/**
	 * Create a new user with the given parameters.
	 * 
	 * @param user    		User object as POST Json body
	 * @return 				The newly created User
	 */
	@PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono createUser (@Valid @RequestBody Userinfo user) {

		// Encrypt the user's password before saving
		user.setPassword(bcryptEncoder.encode(user.getPassword()));

		// DAOUser newUser = new DAOUser();
		// newUser.setUsername(user.getUsername());
		// newUser.setPassword(bcryptEncoder.encode(user.getPassword()));
		// newUser.setRole(user.getRole());

        return userinfoRepository.save(user);
    }

	 /**
	 * Fetch a user with the given username.
	 * 
	 * @param username    	String
	 * @return 				The User if found.
	 */
	@GetMapping("/{username}")
	public Mono<Userinfo> byUsername(@PathVariable("username") String username) {
		logger.info("user-service byUsername() called: " + username);
		return userinfoRepository.findByUsername(username);
	}

}
