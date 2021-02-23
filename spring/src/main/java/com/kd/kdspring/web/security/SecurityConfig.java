package com.kd.kdspring.web.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// ------------------------------------------
// Configuration Settings for all Security in the app
// ------------------------------------------
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter{
		
	@Autowired
	MyUserDetailsService userDetailsService;

	@Autowired
	private MyJwtAuthorisationFilter customJwtAuthorisationFilter;
	
	@Autowired
  	private JwtAuthenticationEntryPoint unauthorizedHandler;

	// ------------------------------------------
	// Bcrypt Password Encoder Bean for hashing passwords passed by the client
	// ------------------------------------------
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
	
	// ------------------------------------------
	// Custom Login filter that will handle Login POST and return a JWT token on successful login
	// ------------------------------------------
	@Bean
	public MyJwtAuthenticationFilter getJwtAuthenticationFilter() throws Exception {
		// Create the filter. Our parent class requires us to assign an AuthenticationManager
		final MyJwtAuthenticationFilter filter = new MyJwtAuthenticationFilter();
		filter.setAuthenticationManager(authenticationManager());
		// By default, UsernamePasswordAuthenticationFilter listens to "/login" path. 
		// !!!!!!!!! In our case, we use a different path. So, we need to override the defaults.
    	// filter.setFilterProcessesUrl("/api/auth/login");
    	return filter;
	}

	// ------------------------------------------
	// Associate our custom User Details Service and Password Encoder with the
	// AuthenticationManager
	// ------------------------------------------
	@Override
	public void configure(AuthenticationManagerBuilder auth) throws Exception
	{
		auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
	}

	// ------------------------------------------
	// Create the AuthenticationManager Bean
	// ------------------------------------------
	@Bean
	@Override
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}
	
/* 	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
			.authorizeRequests()
				.antMatchers("/", "/home").permitAll()
				.anyRequest().authenticated()
				.and()
			.formLogin()
				.loginPage("/login")
				.permitAll()
				.and()
			.logout()
				.permitAll();
	} */

	// ------------------------------------------
	// Configure Http Security to use our custom JWT Authentication Filter to validate JWT tokens
	// and our custom Jwt Authentication EntryPoint to return HTTP errors when the validation
	// fails.
	// ------------------------------------------
	@Override
	protected void configure(HttpSecurity httpSecurity) throws Exception {
		// We don't need CSRF for this example
		httpSecurity.csrf().disable()
			
			.authorizeRequests()
				// All requestes for accounts require Admin role
				.antMatchers("/accounts/**").hasRole("ADMIN")
				// All requests to the root page require User or Admin Role
				.antMatchers("/").hasAnyRole("ADMIN","USER")
				// Allow anyone including unauthenticated users to access the login URL
				.antMatchers("/authenticate").permitAll()
				// All other requests must be authenticated
				.anyRequest().authenticated()
			// Add our Custom filter to handle Login POST
			.and().addFilter(getJwtAuthenticationFilter())
			// When JWT validation fails return HTTP error using our custom Entry Point
			.exceptionHandling()
				.authenticationEntryPoint(unauthorizedHandler)
			// Use stateless session; session won't be used to store user's state.
			.and().sessionManagement()
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
			/* .and().formLogin()
				.loginPage("/kdlogin")
				.loginProcessingUrl("/kdlogin")
				.permitAll()
			.and().logout()
				.permitAll(); */
			// Display a Custom Login Form at this URL
			.and().formLogin()
				.loginPage("/login")
				.permitAll()
				.and()
			.logout()
				.permitAll();	

			// Add our custom filter to validate the JWT tokens with every request
			httpSecurity.addFilterBefore(customJwtAuthorisationFilter, 
				UsernamePasswordAuthenticationFilter.class);
		}
}