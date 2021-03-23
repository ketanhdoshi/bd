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

// Custom User Details service to retrieve user information given a username. We have two 
// hardcoded users, viz. "user" and "admin" along with their passwords and roles. For all
// other users, we look them up from a back-end User Service.
@Service
public class MyUserDetailsService implements UserDetailsService {

    protected Logger logger = Logger.getLogger(MyUserDetailsService.class.getName());

	@Autowired
    protected WebUserService userService;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<SimpleGrantedAuthority> roles=null;
        
        logger.info("loadUserByUsername: " + username);

		if(username.equals("admin"))
		{
            roles = Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN"));
            return new User("admin", "$2y$12$I0Di/vfUL6nqwVbrvItFVOXA1L9OW9kLwe.1qDPhFzIJBpWl76PAe", roles);
		}
		else if(username.equals("user"))
		{
            roles = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));
            return new User("user", "$2y$12$VfZTUu/Yl5v7dAmfuxWU8uRfBKExHBWT1Iqi.s33727NoxHrbZ/h2", roles);
		}
		else {
			// Check the back-end User Service for a matching user in the MySql database
			UserInfo user = userService.findByUsername(username);
			if (user != null) { // found matching user
				roles = Arrays.asList(new SimpleGrantedAuthority(user.getRoles()));
				return new User(user.getUsername(), user.getPassword(), roles);	
			}
		}
		throw new UsernameNotFoundException("User not found with username: " + username);
	}
}
