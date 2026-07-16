package com.smartlibrary.web.superadmin.library;

import com.smartlibrary.entity.Author;
import com.smartlibrary.repository.AuthorRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Objects;

@Controller
@RequestMapping("/superadmin/library/authors")
public class SuperAdminLibraryAuthorController {

    private final AuthorRepository authorRepository;

    public SuperAdminLibraryAuthorController(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("authors", authorRepository.findAll());
        return "superadmin/library/authors";
    }

    @PostMapping
    public String add(@RequestParam String name, RedirectAttributes ra) {
        Author a = new Author();
        a.setName(name.trim());
        authorRepository.save(a);
        ra.addFlashAttribute("success", "Author added");
        return "redirect:/superadmin/library/authors";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable("id") Long id, @RequestParam String name, RedirectAttributes ra) {
        Author a = authorRepository.findById(Objects.requireNonNull(id)).orElseThrow();
        a.setName(name.trim());
        authorRepository.save(a);
        ra.addFlashAttribute("success", "Updated");
        return "redirect:/superadmin/library/authors";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            authorRepository.deleteById(Objects.requireNonNull(id));
            ra.addFlashAttribute("success", "Deleted");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: may be in use");
        }
        return "redirect:/superadmin/library/authors";
    }
}
