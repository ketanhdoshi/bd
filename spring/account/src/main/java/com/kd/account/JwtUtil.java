package com.kd.account;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

// ------------------------------------------
// Utility to handle creation and validation of JWT tokens.
// ------------------------------------------
@Service
public class JwtUtil {

	protected Logger logger = Logger.getLogger(JwtUtil.class.getName());

	private String secret;
	private SecretKey secretKey;
	private int jwtExpirationInMs;

	// ------------------------------------------
	// Get JWT secret key from properties
	// ------------------------------------------
	@Value("${jwt.secret}")
	public void setSecret(String secret) {
		this.secret = secret;
		var encodedSecret = Base64.getEncoder().encodeToString(secret.getBytes());
        this.secretKey = Keys.hmacShaKeyFor(encodedSecret.getBytes(StandardCharsets.UTF_8));
	}
	
	// ------------------------------------------
	// Get token JWT expiration duration from properties
	// ------------------------------------------
	@Value("${jwt.jwtExpirationInMs}")
	public void setJwtExpirationInMs(int jwtExpirationInMs) {
		this.jwtExpirationInMs = jwtExpirationInMs;
	}

	// ------------------------------------------
	// Generate token for user. Called during Login.
	// ------------------------------------------
	public String createToken (Collection<? extends GrantedAuthority> roles, String username) {

		// If the user has the Admin role, add an Admins claim into the token. 
		// Similarly if the user has User role, add an User claim into the token
		Map<String, Object> claims = new HashMap<>();
		for (GrantedAuthority role: roles) {
			String roleAuthority = role.getAuthority();
			switch (roleAuthority) {
				case "ROLE_ADMIN":
					claims.put("isAdmin", true);
					break;
				case "ROLE_USER":
					claims.put("isUser", true);
					break;
				default:
					break;
			}
		}

		// Generate the token using the claims the user name.
		return doGenerateToken(claims, username);
	}

	// ------------------------------------------
	// Call the JWT library to generate a token with the given values
	// ------------------------------------------
	private String doGenerateToken(Map<String, Object> claims, String subject) {
		return Jwts.builder()
				.setClaims(claims)
				.setSubject(subject)
				.setIssuedAt(new Date(System.currentTimeMillis()))
				.setExpiration(new Date(System.currentTimeMillis() + jwtExpirationInMs))
				.signWith(this.secretKey, SignatureAlgorithm.HS512)
				.compact();
	}

	// ------------------------------------------
	// Validate the previously generated JWT token that the client passes in with every API request.
	// ------------------------------------------
	public boolean validateToken(String authToken) {
		try {
			// Parse the Jwt token - if invalid in any way, an exception is thrown
			Jws<Claims> claims = Jwts.parserBuilder()
									.setSigningKey(secretKey)
									.build()
									.parseClaimsJws(authToken);

			// Here we know that the Jwt token has not been tampered with
			logger.info("expiration date: {}" + claims.getBody().getExpiration());
			return true;
		} catch (JwtException ex) {
			// Exception is thrown if the JWT is invalid in any way

			String msg = "Invalid Credentials";
			if (ex instanceof ExpiredJwtException) {
				msg = "Expired token";
			}
			logger.info("Token validation failed:" + msg + ex.getMessage());
			return false;
			// throw new BadCredentialsException(msg, ex);
		}
	}
	
	// ------------------------------------------
	// ------------------------------------------
	public String getUsernameFromToken(String authToken) {
		// Parse the Jwt token
		Claims claims = Jwts.parserBuilder()
							.setSigningKey(secretKey)
							.build()
							.parseClaimsJws(authToken).getBody();

		return claims.getSubject();
	}

	// ------------------------------------------
	// ------------------------------------------
	public List<SimpleGrantedAuthority> getRolesFromToken(String authToken) {
		// Parse the Jwt token
		Claims claims = Jwts.parserBuilder()
							.setSigningKey(secretKey)
							.build()
							.parseClaimsJws(authToken).getBody();
		Boolean isAdmin = claims.get("isAdmin", Boolean.class);
		Boolean isUser = claims.get("isUser", Boolean.class);

		List<SimpleGrantedAuthority> roles = null;
		if (isAdmin != null && isAdmin == true) {
			roles = Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN"));
		}
		if (isUser != null && isUser == true) {
			roles = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));
		}
		return roles;
	}
}