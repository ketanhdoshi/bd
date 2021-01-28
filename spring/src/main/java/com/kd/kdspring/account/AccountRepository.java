package com.kd.kdspring.account;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

// This interface will be Auto-Implemented by Spring into a Bean called accountRepository
public interface AccountRepository extends CrudRepository<Account, Long> {
    /**
	 * Find an account with the specified account number.
	 *
	 * @param accountNumber
	 * @return The account if found, null otherwise.
	 */
	public Account findByNumber(String accountNumber);
}
