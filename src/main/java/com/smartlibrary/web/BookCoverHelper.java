package com.smartlibrary.web;

import com.smartlibrary.entity.Book;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component("bookCoverHelper")
public class BookCoverHelper {

    private static final String[][] CATEGORY_PALETTES = {
            {"#166534", "#22c55e"},
            {"#1d4ed8", "#3b82f6"},
            {"#7c3aed", "#a78bfa"},
            {"#b45309", "#f59e0b"},
            {"#be123c", "#fb7185"},
            {"#0f766e", "#14b8a6"},
            {"#4338ca", "#6366f1"},
            {"#0369a1", "#38bdf8"},
    };

    public String gradientFor(Book book) {
        int index = paletteIndex(book);
        String[] palette = CATEGORY_PALETTES[index % CATEGORY_PALETTES.length];
        return "linear-gradient(145deg, " + palette[0] + " 0%, " + palette[1] + " 100%)";
    }

    public String accentFor(Book book) {
        int index = paletteIndex(book);
        return CATEGORY_PALETTES[index % CATEGORY_PALETTES.length][1];
    }

    public String initials(Book book) {
        if (book == null || book.getTitle() == null || book.getTitle().isBlank()) {
            return "BK";
        }
        String[] words = book.getTitle().trim().split("\\s+");
        if (words.length >= 2) {
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase(Locale.ROOT);
        }
        String word = words[0];
        return word.length() >= 2
                ? word.substring(0, 2).toUpperCase(Locale.ROOT)
                : word.toUpperCase(Locale.ROOT);
    }

    public String shortTitle(Book book) {
        if (book == null || book.getTitle() == null) {
            return "";
        }
        String title = book.getTitle().trim();
        return title.length() > 42 ? title.substring(0, 39) + "..." : title;
    }

    public String authorLine(Book book) {
        if (book == null || book.getAuthor() == null || book.getAuthor().getName() == null) {
            return "Unknown Author";
        }
        return book.getAuthor().getName();
    }

    public String categoryLine(Book book) {
        if (book == null || book.getCategory() == null || book.getCategory().getName() == null) {
            return "General";
        }
        return book.getCategory().getName();
    }

    private int paletteIndex(Book book) {
        String key = categoryLine(book) + "|" + (book != null ? book.getTitle() : "");
        return Math.floorMod(key.hashCode(), CATEGORY_PALETTES.length);
    }
}
