package com.kd.kdspring.account;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
public class AccountController {

	protected Logger logger = Logger.getLogger(AccountController.class.getName());
	protected AccountRepository accountRepository;

	/**
	 * Autowired automatically injects the respository of Accounts.
	 * 
	 * @param accountRepository    An account repository implementation.
	 */
	@Autowired
	public AccountController(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;

		//logger.info("AccountRepository says system has " + accountRepository.countAccounts() + " accounts");
	}

	/**
	 * Fetch an account with the specified account number.
	 * 
	 * @param accountNumber    	A numeric, 9 digit account number.
	 * @return 					The account if found.
	 * @throws AccountNotFoundException	If the number is not recognised.
	 */
	@RequestMapping("/accounts/{accountNumber}")
	public Account byNumber(@PathVariable("accountNumber") String accountNumber) {

		logger.info("accounts-service byNumber() invoked: " + accountNumber);
		Account account = accountRepository.findByNumber(accountNumber);
		logger.info("accounts-service byNumber() found: " + account);

		return account;

/* 		if (account == null)
			throw new AccountNotFoundException(accountNumber);
		else {
			return account;
		}
 */	}

}
