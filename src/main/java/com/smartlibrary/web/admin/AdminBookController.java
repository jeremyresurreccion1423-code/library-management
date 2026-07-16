package com.smartlibrary.web.admin;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.dto.BookForm;
import com.smartlibrary.entity.Book;
import com.smartlibrary.repository.AuthorRepository;
import com.smartlibrary.repository.CategoryRepository;
import com.smartlibrary.service.BookService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/books")
public class AdminBookController {

    private final BookService bookService;
    private final CategoryRepository categoryRepository;
    private final AuthorRepository authorRepository;
    private final LibraryProperties libraryProperties;

    public AdminBookController(
            BookService bookService,
            CategoryRepository categoryRepository,
            AuthorRepository authorRepository,
            LibraryProperties libraryProperties) {
        this.bookService = bookService;
        this.categoryRepository = categoryRepository;
        this.authorRepository = authorRepository;
        this.libraryProperties = libraryProperties;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Boolean availableOnly,
            @RequestParam(required = false, defaultValue = "all") String statusFilter,
            Model model) {
        List<Book> books = bookService.search(q, categoryId, authorId, Boolean.TRUE.equals(availableOnly));
        if (statusFilter != null && !statusFilter.isBlank() && !"all".equalsIgnoreCase(statusFilter)) {
            books = books.stream().filter(b -> matchesBookStatus(b, statusFilter)).toList();
        }
        model.addAttribute("books", books);
        model.addAttribute("bookCount", books.size());
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("authors", authorRepository.findAll());
        model.addAttribute("q", q);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("authorId", authorId);
        model.addAttribute("availableOnly", availableOnly);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("defaultFinePerDay", libraryProperties.getFinePerDay());
        return "admin/books";
    }

    private static boolean matchesBookStatus(Book book, String statusFilter) {
        return switch (statusFilter.toLowerCase()) {
            case "available" -> book.getAvailableCopies() > 0 && book.getAvailableCopies() == book.getTotalCopies();
            case "borrowed" -> book.getAvailableCopies() < book.getTotalCopies();
            case "unavailable" -> book.getAvailableCopies() == 0;
            default -> true;
        };
    }

    @GetMapping("/new")
    public String formNew(Model model) {
        model.addAttribute("book", new BookForm());
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("authors", authorRepository.findAll());
        model.addAttribute("defaultFinePerDay", libraryProperties.getFinePerDay());
        return "admin/book-form";
    }

    @GetMapping("/{id}/edit")
    public String formEdit(@PathVariable("id") Long id, Model model) {
        Book book = bookService.findById(id).orElseThrow();
        populateBookFormModel(model, toBookForm(book));
        return "admin/book-form";
    }

    @PostMapping("/save")
    public String save(
            @Valid @ModelAttribute("book") BookForm bookForm,
            BindingResult bindingResult,
            @RequestParam(required = false) MultipartFile ebookFile,
            Model model,
            RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            populateBookFormModel(model, bookForm);
            model.addAttribute("error", bindingResult.getFieldErrors().stream()
                    .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : error.getField() + " is invalid")
                    .collect(Collectors.joining(" ")));
            return "admin/book-form";
        }
        try {
            Book savedBook = bookService.saveBook(
                    bookForm.getId(),
                    bookForm.getIsbn(),
                    bookForm.getTitle(),
                    bookForm.getBarcode(),
                    bookForm.getTotalCopies(),
                    bookForm.getCategoryId(),
                    bookForm.getAuthorId(),
                    bookForm.getFinePerDay());
            Long savedId = savedBook.getId();
            if (ebookFile != null && !ebookFile.isEmpty()) {
                try {
                    bookService.attachEbook(savedId, ebookFile);
                    ra.addFlashAttribute("success", "Book saved. E-book uploaded.");
                } catch (Exception e) {
                    ra.addFlashAttribute("warning", "Book saved but e-book upload failed: " + e.getMessage());
                }
            } else {
                ra.addFlashAttribute("success", "Book saved");
            }
            return "redirect:/admin/books";
        } catch (IllegalArgumentException | IllegalStateException e) {
            populateBookFormModel(model, bookForm);
            model.addAttribute("error", e.getMessage());
            return "admin/book-form";
        }
    }

    private void populateBookFormModel(Model model, BookForm bookForm) {
        model.addAttribute("book", bookForm);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("authors", authorRepository.findAll());
        model.addAttribute("defaultFinePerDay", libraryProperties.getFinePerDay());
    }

    private BookForm toBookForm(Book book) {
        BookForm form = new BookForm();
        form.setId(book.getId());
        form.setIsbn(book.getIsbn());
        form.setTitle(book.getTitle());
        form.setBarcode(book.getBarcode());
        form.setTotalCopies(book.getTotalCopies());
        form.setFinePerDay(book.getFinePerDay());
        if (book.getCategory() != null) {
            form.setCategoryId(book.getCategory().getId());
        }
        if (book.getAuthor() != null) {
            form.setAuthorId(book.getAuthor().getId());
        }
        return form;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes ra) {
        bookService.deleteBook(id);
        ra.addFlashAttribute("success", "Book removed");
        return "redirect:/admin/books";
    }

    @PostMapping("/{id}/ebook")
    public String uploadEbook(@PathVariable("id") Long id, @RequestParam MultipartFile file, RedirectAttributes ra) {
        try {
            bookService.attachEbook(id, file);
            ra.addFlashAttribute("success", "E-book uploaded");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/books/" + id + "/edit";
    }
}
