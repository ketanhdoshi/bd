package com.kd.kdspring.book;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;

import com.kd.kdspring.exception.BookNotFoundException;
import com.kd.kdspring.exception.BookIdMismatchException;

import java.util.List;

// URL's start with /api/books (after Application path)
@RestController
@RequestMapping("/api/books")
public class BookController {
    // get the bean called bookRepository Which is auto-generated by Spring
    @Autowired
    private BookRepository bookRepository;

    @PostMapping // Map POST Requests
    @ResponseStatus(HttpStatus.CREATED)
    public Book create(@RequestBody Book book) {
        return bookRepository.save(book);
    }

    @GetMapping // Map GET Requests
    public Iterable<Book> findAll() {
        return bookRepository.findAll();
    }

    @GetMapping("/title/{bookTitle}")
    public List<Book> findByTitle(@PathVariable String bookTitle) {
        return bookRepository.findByTitle(bookTitle);
    }

    @GetMapping("/{id}")
    public Book findOne(@PathVariable long id) {
        return bookRepository.findById(id)
          .orElseThrow(BookNotFoundException::new);
    }

    @DeleteMapping("/{id}") // Map DELETE Requests
    public void delete(@PathVariable long id) {
        bookRepository.findById(id)
          .orElseThrow(BookNotFoundException::new);
        bookRepository.deleteById(id);
    }

    @PutMapping("/{id}") // Map PUT Requests
    public Book updateBook(@RequestBody Book book, @PathVariable long id) {
        if (book.getId() != id) {
            throw new BookIdMismatchException();
        }
        bookRepository.findById(id)
          .orElseThrow(BookNotFoundException::new);
        return bookRepository.save(book);
    }
}