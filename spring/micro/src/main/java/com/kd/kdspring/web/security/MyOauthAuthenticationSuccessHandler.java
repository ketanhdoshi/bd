package com.kd.kdspring.web.security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.http.HttpHeaders;

// ------------------------------------------
// Web UI Clients use Oauth login. After the user is successfully authenticated, we can get the
// identity of the user (but not their credentials). Here we use the user name and roles to
// generate a JWT token which is returned in the HTTP Response as a Cookie.
// ------------------------------------------

@Component
public class MyOauthAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    // Utility to generate JWT tokens
	@Autowired
    private JwtUtil jwtTokenUtil;

	@Autowired
	MyUserDetailsService userDetailsService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication auth) throws IOException, ServletException {
        if (response.isCommitted()) {
            return;
        }

        // Username and Roles that were returned by Oauth. But we don't make use of the Roles.
        String username = auth.getName();
        Collection<? extends GrantedAuthority> oauthRoles = auth.getAuthorities();

        // Fetch the Role information from the User Service's database
		UserDetails userDetails = userDetailsService.loadOauthUserByUsername(username);
        Collection<? extends GrantedAuthority> roles = userDetails.getAuthorities();
		logger.info("Oauth: " + username + roles);

        final String token = jwtTokenUtil.createToken (roles, username);

        Cookie cookie = new Cookie("jwt", token);
		cookie.setMaxAge(30 * 60); 	// expires in 30 minutes
		cookie.setSecure(false);	// Send over HTTP not HTTPS
        cookie.setHttpOnly(true);
        // Must set the path, otherwise the cookie is not retained after redirect
        cookie.setPath("/");        

		//Add cookie to response
		response.addCookie(cookie);

        // Return the token in the Authorization Response Header.
		String TOKEN_PREFIX = "Bearer ";
        response.addHeader(HttpHeaders.AUTHORIZATION, TOKEN_PREFIX + token);

        OAuth2User oauth2User = (OAuth2User) auth.getPrincipal();
        String targetUrl = getDefaultTargetUrl();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}