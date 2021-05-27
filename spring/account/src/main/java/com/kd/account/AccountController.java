package com.kd.account;

import java.util.logging.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// ------------------------------------------
// Reactive REST Controller supports all the CRUD API operations for Account object
// ------------------------------------------

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

	protected Logger logger = Logger.getLogger(AccountController.class.getName());
	
	// Inject the Reactive Persistent Repository for Account. Provides all the CRUD and Query operations for
	// the database out of the box.
	@Autowired
	protected AccountRepository accountRepository;

	/**
	 * Fetch an account with the specified account number.
	 * 
	 * @param accountNumber    	A numeric, 9 digit account number.
	 * @return 					The account if found.
	 */
	@GetMapping("/{accountNumber}")
	public Mono<Account> byNumber(@PathVariable("accountNumber") String accountNumber) {
		// The URL request path contains the account number

		logger.info("accounts-service byNumber: " + accountNumber);
		return accountRepository.findByNumber(accountNumber);
	}
	
	/**
	 * Fetch all accounts.
	 * 
	 * @return 					Flux with all accounts
	 */
	@GetMapping
	public Flux<Account> findAll() {
		logger.info("accounts-service findAll");
		return accountRepository.findAll();
	}

	/**
	 * Create a new account
	 * 
	 * @param accountNumber    	A numeric, 9 digit account number.
	 * @return 					The account if found.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Mono<Account> create(@RequestBody Account account) {
		// Request body contains the JSON data that gets deserialised into an Account object.
		
		logger.info("accounts-service create: " + account);
	 	return accountRepository.save(account);
	}

	/**
	 * Update an account. Only the account owner and balance can be updated.
	 * 
	 * @param accountNumber    	A numeric, 9 digit account number.
	 * @return 					The updated account if found, or HTTP error status
	 */
	@PutMapping("/{accountNumber}")
	public  Mono<ResponseEntity<Account>> updatebyNumber(@RequestBody Account account, @PathVariable("accountNumber") String accountNumber) {
		// Request body contains the JSON data that gets deserialised into an Account object.
		// The URL request path contains the account number

		// Find the account to be updated and modify it using values from the incoming request
		 Mono<Account> updatedAccount = accountRepository.findByNumber(accountNumber)
		 	.flatMap(dbAccount -> {
				dbAccount.setOwner(account.getOwner());
				dbAccount.setBalance(account.getBalance());
				return accountRepository.save(dbAccount);
			});
	
		// Return the updated account in the response if successful. Else return HTTP error
		return updatedAccount
			.map(acct -> ResponseEntity.ok(acct))
            .defaultIfEmpty(ResponseEntity.badRequest().build());
	}
	
	/**
	 * Delete an account.
	 * 
	 * @param accountNumber    	A numeric, 9 digit account number.
	 * @return 					HTTP success or error status
	 */
	@DeleteMapping("/{accountNumber}")
    public Mono<ResponseEntity<Void>> deleteByNumber(@PathVariable("accountNumber") String accountNumber){
		// The URL request path contains the account number

		// Find the account to be deleted and delete it. Then return the deleted account.
		Mono<Account> deletedAccount = accountRepository.findByNumber(accountNumber)
			.flatMap(dbAccount -> accountRepository.delete(dbAccount)
				.then(Mono.just(dbAccount)));

		// Return the deleted account in the response if successful. Else return HTTP error
		return deletedAccount
            .map(r -> ResponseEntity.ok().<Void>build())
            .defaultIfEmpty(ResponseEntity.notFound().build());
	}
}
