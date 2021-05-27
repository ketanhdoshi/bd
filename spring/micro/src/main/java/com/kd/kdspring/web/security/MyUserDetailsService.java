package com.kd.kdspring.web.security;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import com.kd.kdspring.web.WebUserService;
import com.kd.kdspring.user.UserInfo;

// ------------------------------------------
// Custom User Details service to retrieve user information given a username, during the
// Authentication flow. We have two hardcoded users, viz. "user" and "admin" along with 
// their passwords and roles. For all other users, we look them up from a 
// back-end User Service.
// ------------------------------------------

@Service
public class MyUserDetailsService implements UserDetailsService {

    protected Logger logger = Logger.getLogger(MyUserDetailsService.class.getName());

	@Autowired
    protected WebUserService userService;

   	// ------------------------------------------
   	// Is username an Oauth username. We use a hack - all Oauth usernames
	// have to be a numeric integer string. In reality, we would have a flag
	// in the user database for this.
   	// ------------------------------------------
	public static boolean isOauthUsername(String username) {
		if (username == null) {
			return false;
		}
		try {
			Integer i = Integer.parseInt(username);
		} catch (NumberFormatException nfe) {
			return false;
		}
		return true;
	}

   	// ------------------------------------------
   	// Load User Details for the given username
   	// ------------------------------------------
   	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<SimpleGrantedAuthority> roles=null;
        
        logger.info("loadUserByUsername: " + username);

		if(username.equals("admin"))
		{
			// Hard-coded 'admin' user with hard-coded roles
            roles = Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN"));
			// Create User Details with hard-coded username, encrypted password and roles
            return new User("admin", "$2y$12$I0Di/vfUL6nqwVbrvItFVOXA1L9OW9kLwe.1qDPhFzIJBpWl76PAe", roles);
		}
		else if(username.equals("user"))
		{
			// Hard-coded 'user' user with hard-coded roles
            roles = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));
			// Create User Details with hard-coded username, encrypted password and roles
            return new User("user", "$2y$12$VfZTUu/Yl5v7dAmfuxWU8uRfBKExHBWT1Iqi.s33727NoxHrbZ/h2", roles);
		}
		// Only for non-Oauth users. Oauth users use a different method.
		else if(!isOauthUsername(username)) {
			// For all other users, check the back-end User Service for a matching user in the MySql database
			UserInfo user = userService.findByUsername(username);
			if (user != null) { // found matching user
				// Get the user's roles
				roles = Arrays.asList(new SimpleGrantedAuthority(user.getRoles()));
				// Create User Details with user's name, password and roles
				return new User(user.getUsername(), user.getPassword(), roles);	
			}
		}
		// Exception if user not found(or is an Oauth user)
		throw new UsernameNotFoundException("User not found with username: " + username);
	}

	// ------------------------------------------
   	// Load User Details from the database for the given Oauth username. This is only to get the roles.
   	// ------------------------------------------
	public UserDetails loadOauthUserByUsername(String username) throws UsernameNotFoundException {
        List<SimpleGrantedAuthority> roles=null;
        
        logger.info("loadOauthUserByUsername: " + username);

		if(isOauthUsername(username)) {
			// Check the back-end User Service for a matching user in the MySql database
			UserInfo user = userService.findByUsername(username);
			if (user != null) { // found matching user
				// Get the user's roles
				roles = Arrays.asList(new SimpleGrantedAuthority(user.getRoles()));
				// Create User Details with user's roles. We don't really care about the 
				// username and password.
				return new User(user.getUsername(), user.getPassword(), roles);	
			}
			
		}
		// Exception if not-Oauth user, or user not found
		throw new UsernameNotFoundException("User not found with username: " + username);
	}
}
