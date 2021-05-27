package com.kd.kdspring.account;

import java.math.BigDecimal;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;

import lombok.Data;

// ------------------------------------------
// Model class for Account object
//
// It's a copy of the Account model used by the Account Microservice. But we don't
// want to share the code to keep the two services de-coupled. Theoretically the
// microservice could be written in a different programming language. So the only thing
// they should agree on is the serialised JSON format sent over the wire.
// ------------------------------------------

// @Data, from Lombok generates boilerplate code for getters/setters that is normally 
// needed with POJOs
@Data
@Entity // Used by Java Persistence
@Table(name= "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    @Column(name="account_id")
    private Long id;

    @NotEmpty(message="* Please Enter Account Number")
    // No need for @Column as column name is the same
    private String number;

    @Column(name = "name")
    @NotEmpty(message="* Please Enter Account Name")
	private String owner;

    @NotEmpty(message="* Please Enter Account Balance")
    private BigDecimal balance;

}
