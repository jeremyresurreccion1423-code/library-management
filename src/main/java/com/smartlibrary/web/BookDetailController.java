package com.smartlibrary.web;

import com.smartlibrary.service.BookService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class BookDetailController {

    private final BookService bookService;

    public BookDetailController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/books/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        var book = bookService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found with ID: " + id));
        model.addAttribute("book", book);
        model.addAttribute("ebook", bookService.ebookForBook(id).orElse(null));
        return "book-detail";
    }
}
