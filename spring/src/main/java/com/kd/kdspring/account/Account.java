package com.kd.kdspring.account;

import java.math.BigDecimal;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;

import lombok.Data;

// @Data, from Lombok generates boilerplate code for getters/setters that is normally 
// needed with POJOs
@Data
@Entity
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
