package com.kd.kdspring.user;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;

import lombok.Data;

// @Data, from Lombok generates boilerplate code for getters/setters that is normally 
// needed with POJOs
@Data
@Entity
@Table(name= "users")
public class UserInfo {
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    @Column(name="user_id")
    private Long id;

    @NotEmpty(message="* Please Enter User Name")
    // No need for @Column as column name is the same
    private String username;

    @NotEmpty(message="* Please Enter Password")
	private String password;

    @NotEmpty(message="* Please Enter Roles")
    private String roles;
}
