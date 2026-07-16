package com.smartlibrary.web.superadmin.library;

import com.smartlibrary.entity.Category;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.CategoryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;

@Controller
@RequestMapping("/superadmin/library/categories")
public class SuperAdminLibraryCategoryController {

    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;

    public SuperAdminLibraryCategoryController(CategoryRepository categoryRepository, BookRepository bookRepository) {
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryRepository.findAll());
        return "superadmin/library/categories";
    }

    @PostMapping
    public String add(@RequestParam String name, RedirectAttributes ra) {
        if (categoryRepository.existsByNameIgnoreCase(name.trim())) {
            ra.addFlashAttribute("error", "Category already exists");
        } else {
            Category c = new Category();
            c.setName(name.trim());
            categoryRepository.save(c);
            ra.addFlashAttribute("success", "Category added");
        }
        return "redirect:/superadmin/library/categories";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable("id") Long id, @RequestParam String name, RedirectAttributes ra) {
        Category c = categoryRepository.findById(Objects.requireNonNull(id)).orElseThrow();
        c.setName(name.trim());
        categoryRepository.save(c);
        ra.addFlashAttribute("success", "Updated");
        return "redirect:/superadmin/library/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            Long categoryId = Objects.requireNonNull(id);
            Category category = categoryRepository.findById(categoryId).orElseThrow();
            long booksUsingCategory = bookRepository.countByCategory_Id(categoryId);
            if (booksUsingCategory > 0) {
                ra.addFlashAttribute("error",
                        "Cannot delete category \"" + category.getName() + "\" because it is used by " + booksUsingCategory + " book(s).");
                return "redirect:/superadmin/library/categories";
            }
            categoryRepository.deleteById(categoryId);
            ra.addFlashAttribute("success", "Deleted");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: may be in use by books");
        }
        return "redirect:/superadmin/library/categories";
    }
}
