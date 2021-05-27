package com.kd.account;

import java.math.BigDecimal;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

// ------------------------------------------
// Model class for Account object
// ------------------------------------------

// @Data, from Lombok generates boilerplate code for getters/setters that is normally 
// needed with POJOs
@Data
@Table("accounts") // Table name in the DB
public class Account {
    @Id
    //@GeneratedValue(strategy= GenerationType.AUTO)
    @Column("account_id")
    private Long id;

    //@NotEmpty(message="* Please Enter Account Number")
    // No need for @Column as column name is the same as the DB
    private String number;

    @Column("name")
    // @NotEmpty(message="* Please Enter Account Name")
	private String owner;

    // @NotEmpty(message="* Please Enter Account Balance")
    private BigDecimal balance;
}
