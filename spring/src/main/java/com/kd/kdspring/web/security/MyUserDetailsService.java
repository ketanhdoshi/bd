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

// Custom User Details service to retrieve user information given a username. This would
// normally be fetched from a database, but for now we support only two hardcoded users, viz.
// "user" and "admin" and hardcode their passwords and roles.
@Service
public class MyUserDetailsService implements UserDetailsService {

    protected Logger logger = Logger.getLogger(MyUserDetailsService.class.getName());

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
		throw new UsernameNotFoundException("User not found with username: " + username);
	}
}
