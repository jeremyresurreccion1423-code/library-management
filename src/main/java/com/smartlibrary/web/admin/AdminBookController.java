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
            Model model) {
        model.addAttribute("books", bookService.search(q, categoryId, authorId, Boolean.TRUE.equals(availableOnly)));
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("authors", authorRepository.findAll());
        model.addAttribute("q", q);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("authorId", authorId);
        model.addAttribute("availableOnly", availableOnly);
        model.addAttribute("defaultFinePerDay", libraryProperties.getFinePerDay());
        return "admin/books";
    }

    @GetMapping("/new")
    public String formNew(Model model) {
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("authors", authorRepository.findAll());
        model.addAttribute("defaultFinePerDay", libraryProperties.getFinePerDay());
        return "admin/book-form";
    }

    @GetMapping("/{id}/edit")
    public String formEdit(@PathVariable("id") Long id, Model model) {
        model.addAttribute("book", bookService.findById(id).orElseThrow());
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("authors", authorRepository.findAll());
        model.addAttribute("defaultFinePerDay", libraryProperties.getFinePerDay());
        return "admin/book-form";
    }

    @PostMapping("/save")
    public String save(
            @Valid @ModelAttribute BookForm bookForm,
            BindingResult bindingResult,
            @RequestParam(required = false) MultipartFile ebookFile,
            Model model,
            RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categoryRepository.findAll());
            model.addAttribute("authors", authorRepository.findAll());
            model.addAttribute("book", bookForm);
            model.addAttribute("defaultFinePerDay", libraryProperties.getFinePerDay());
            return "admin/book-form";
        }
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
