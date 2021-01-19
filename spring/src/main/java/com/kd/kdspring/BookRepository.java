package com.kd.kdspring;

import org.springframework.data.repository.CrudRepository;

import com.kd.kdspring.model.Book;

import java.util.List;

public interface BookRepository extends CrudRepository<Book, Long> {
    List<Book> findByTitle(String title);
}