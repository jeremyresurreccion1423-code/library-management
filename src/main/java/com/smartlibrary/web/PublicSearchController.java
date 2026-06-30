package com.smartlibrary.web;

import com.smartlibrary.repository.AuthorRepository;
import com.smartlibrary.repository.CategoryRepository;
import com.smartlibrary.service.BookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PublicSearchController {

    private static final Logger logger = LoggerFactory.getLogger(PublicSearchController.class);

    private final BookService bookService;
    private final CategoryRepository categoryRepository;
    private final AuthorRepository authorRepository;

    public PublicSearchController(
            BookService bookService,
            CategoryRepository categoryRepository,
            AuthorRepository authorRepository) {
        this.bookService = bookService;
        this.categoryRepository = categoryRepository;
        this.authorRepository = authorRepository;
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Boolean availableOnly,
            @RequestParam(required = false) String isbn,
            Model model) {
        
        logger.debug("Search request: q={}, categoryId={}, authorId={}, availableOnly={}, isbn={}", 
                q, categoryId, authorId, availableOnly, isbn);
        
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("authors", authorRepository.findAll());
        
        if (isbn != null && !isbn.isBlank()) {
            model.addAttribute("books", bookService.findByIsbn(isbn.trim()));
            model.addAttribute("isbn", isbn);
            logger.info("ISBN search performed: {}", isbn);
        } else {
            model.addAttribute(
                    "books",
                    bookService.search(q, categoryId, authorId, Boolean.TRUE.equals(availableOnly)));
            model.addAttribute("q", q);
            model.addAttribute("categoryId", categoryId);
            model.addAttribute("authorId", authorId);
            model.addAttribute("availableOnly", availableOnly);
            logger.info("Text search performed: query={}, filters: category={}, author={}, availableOnly={}", 
                    q, categoryId, authorId, availableOnly);
        }
        
        return "search";
    }

    @GetMapping("/books-categories")
    public String booksCategories(Model model) {
        logger.debug("Browse books by categories");
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("books", bookService.search(null, null, null, null));
        return "books-categories";
    }

    @GetMapping("/scan")
    public String scanPage() {
        logger.debug("QR code scanner page accessed");
        return "scan";
    }
}
