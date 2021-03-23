package com.kd.kdspring.book;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

// This interface will be Auto-Implemented by Spring into a Bean called bookRepository
public interface BookRepository extends CrudRepository<Book, Long> {
    List<Book> findByTitle(String title);
}