package com.kd.kdspring.member;

import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

// @Data, from Lombok generates boilerplate code for getters/setters that is normally 
// needed with POJOs
@Data
@Entity
@Table(name= "members")
public class Member {

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    @Column(name="member_id")
    private Long id;

    @Column(name="first_name")
    @NotEmpty(message="* Please Enter First Name")
    private String firstName;

    @Column(name="last_name")
    @NotEmpty(message="* Please Enter Last Name")
    private String lastName;

    @Email(message="* Please Enter Valid Email Address")
    @NotEmpty(message=" * Please Provide Email Address")
    @Column(name="email")
    private String email;

}