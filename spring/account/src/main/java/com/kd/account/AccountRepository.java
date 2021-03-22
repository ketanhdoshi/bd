package com.kd.account;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.List;

// This interface will be Auto-Implemented by Spring into a Bean called accountRepository
public interface AccountRepository extends ReactiveCrudRepository<Account, Long> {
    /**
	 * Find an account with the specified account number.
	 *
	 * @param accountNumber
	 * @return The account if found, null otherwise.
	 */
	public Mono<Account> findByNumber(String accountNumber);

	/**
	 * Find accounts whose owner name contains the specified string
	 * 
	 * @param partialName
	 *            Any alphabetic string.
	 * @return The list of matching accounts - always non-null, but may be
	 *         empty.
	 */
	//public List<Account> findByOwnerContainingIgnoreCase(String partialName);

	/**
	 * Fetch the number of accounts known to the system.
	 * 
	 * @return The number of accounts.
	 */
	//@Query("SELECT count(*) from Account")
	//public int countAccounts();

}
