package com.kd.kdspring.web.security;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;

// Java object that maps to a HTTP Request for authentication that contains
// username/password in the body of the request. The HTTP Request is then
// deserialised into this object, and passed to our Authentication Controller.
@Data
public class AuthRequest {

    @NotNull @Email
    private String username;
    @NotNull
    private String password;

}