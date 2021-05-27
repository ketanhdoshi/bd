package com.kd.userinfo;

import javax.validation.constraints.NotEmpty;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

// ------------------------------------------
// Model class for User Info object
// ------------------------------------------

// @Data, from Lombok generates boilerplate code for getters/setters that is normally 
// needed with POJOs
@Data
@Table("users")
public class Userinfo {
    @Id
    //@GeneratedValue(strategy= GenerationType.AUTO)
    @Column("user_id")
    private Long id;

    //@NotEmpty(message="* Please Enter User Name")
    // No need for @Column as column name is the same
    private String username;

    //@NotEmpty(message="* Please Enter Password")
	private String password;

    //@NotEmpty(message="* Please Enter Roles")
    private String roles;
}
