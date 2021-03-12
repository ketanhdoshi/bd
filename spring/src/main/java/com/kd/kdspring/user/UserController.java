package com.kd.kdspring.user;

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

import com.kd.kdspring.exception.UserNotFoundException;

@RestController
@RequestMapping("/api/users")
public class UserController {

	protected Logger logger = Logger.getLogger(UserController.class.getName());

	// Inject the User Repository
	@Autowired
	protected UserRepository userRepository;

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
    public UserInfo createUser (@Valid @RequestBody UserInfo user) {

		// Encrypt the user's password before saving
		user.setPassword(bcryptEncoder.encode(user.getPassword()));

		// DAOUser newUser = new DAOUser();
		// newUser.setUsername(user.getUsername());
		// newUser.setPassword(bcryptEncoder.encode(user.getPassword()));
		// newUser.setRole(user.getRole());

        return userRepository.save(user);
    }

	 /**
	 * Fetch a user with the given username.
	 * 
	 * @param username    	String
	 * @return 				The User if found.
	 * @throws UserNotFoundException	If the username is not recognised.
	 */
	@GetMapping("/{username}")
	public UserInfo byUsername(@PathVariable("username") String username) {

		UserInfo user = userRepository.findByUsername(username);
		logger.info("user-service byUsername() found: " + username);

		if (user == null)
			throw new UserNotFoundException(username);
		else {
			return user;
		}
	}

}
