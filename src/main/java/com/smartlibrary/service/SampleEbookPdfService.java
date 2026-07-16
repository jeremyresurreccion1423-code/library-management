package com.smartlibrary.service;

import com.smartlibrary.entity.Book;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class SampleEbookPdfService {

    public void writeSamplePdf(Path path, Book book) throws IOException {
        String title = book != null && book.getTitle() != null ? book.getTitle() : "Library Book";
        String author = book != null && book.getAuthor() != null && book.getAuthor().getName() != null
                ? book.getAuthor().getName()
                : "Unknown Author";
        String category = book != null && book.getCategory() != null && book.getCategory().getName() != null
                ? book.getCategory().getName()
                : "General";

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = PDRectangle.A4.getHeight() - 72f;
                y = writeLine(content, "EduLibrary — Digital Edition", 50f, y, PDType1Font.HELVETICA_BOLD, 20f, 28f);
                y = writeLine(content, safePdfLine(title, 90), 50f, y, PDType1Font.HELVETICA_BOLD, 16f, 24f);
                y = writeLine(content, "Author: " + safePdfLine(author, 80), 50f, y, PDType1Font.HELVETICA, 12f, 20f);
                y = writeLine(content, "Category: " + safePdfLine(category, 80), 50f, y, PDType1Font.HELVETICA, 12f, 28f);

                for (String paragraph : sampleParagraphs(title)) {
                    for (String line : wrapText(paragraph, PDType1Font.HELVETICA, 11f, 480f)) {
                        y = writeLine(content, line, 50f, y, PDType1Font.HELVETICA, 11f, 16f);
                        if (y < 72f) {
                            break;
                        }
                    }
                    y -= 8f;
                    if (y < 100f) {
                        break;
                    }
                }
            }

            document.save(path.toFile());
        }
    }

    private List<String> sampleParagraphs(String title) {
        String topic = safePdfLine(title, 60);
        return List.of(
                "This digital copy is available while your loan is active. Use it for study, review, and reference.",
                "Overview for " + topic + ": focus on understanding core concepts before memorizing details.",
                "Practice workflow: plan your reading, take short notes, and revisit key sections after each chapter.",
                "End of sample content. Administrators can replace this file by uploading a full PDF for this title."
        );
    }

    private float writeLine(
            PDPageContentStream content,
            String text,
            float x,
            float y,
            PDType1Font font,
            float fontSize,
            float stepDown) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - stepDown;
    }

    private List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float width = font.getStringWidth(candidate) / 1000f * fontSize;
            if (width <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                } else {
                    lines.add(word);
                }
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String safePdfLine(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String ascii = value.replaceAll("[^\\x20-\\x7E]", "?").trim();
        return ascii.length() <= maxLength ? ascii : ascii.substring(0, maxLength - 3) + "...";
    }
}
