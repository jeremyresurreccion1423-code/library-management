package com.smartlibrary.config;

import com.smartlibrary.entity.Author;
import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.Category;
import com.smartlibrary.entity.EbookAsset;
import com.smartlibrary.entity.StudentProfile;
import com.smartlibrary.entity.User;
import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.AuthorRepository;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.CategoryRepository;
import com.smartlibrary.repository.EbookAssetRepository;
import com.smartlibrary.repository.StudentProfileRepository;
import com.smartlibrary.repository.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String LIBRARY_SCHEMA = "library";

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final EbookAssetRepository ebookAssetRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final LibraryProperties libraryProperties;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CategoryRepository categoryRepository,
            AuthorRepository authorRepository,
            BookRepository bookRepository,
            EbookAssetRepository ebookAssetRepository,
            StudentProfileRepository studentProfileRepository,
            LibraryProperties libraryProperties,
            JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryRepository = categoryRepository;
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
        this.ebookAssetRepository = ebookAssetRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.libraryProperties = libraryProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        fixSchema();
        ensureAdminContactEmails();
        if (libraryProperties.isStartupMaintenanceEnabled()) {
            fixNullVersions();
            fixInvalidEmails();
        }

        if (!libraryProperties.isSeedEnabled()) {
            log.info("Seed data initialization is disabled (library.seed-enabled=false).");
            return;
        }
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("mercadocarlo645@gmail.com");
            admin.setRole(UserRole.ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
            log.info("Default admin created: username=admin password=admin123 (change after first login)");
        }

        if (userRepository.findByUsername("admin2").isEmpty()) {
            User admin2 = new User();
            admin2.setUsername("admin2");
            admin2.setPassword(passwordEncoder.encode("admin1234"));
            admin2.setEmail("edulibrary67+admin2@gmail.com");
            admin2.setRole(UserRole.ADMIN);
            admin2.setEnabled(true);
            userRepository.save(admin2);
            log.info("Additional admin created: username=admin2 password=admin1234 (change after first login)");
        }

        ensureSampleStudent(
                "student1", "student123", "edulibrary67+student1@gmail.com",
                "2024001", "Juan Dela Cruz", "09123456789", "Computer Science");
        ensureSampleStudent(
                "student2", "student123", "edulibrary67+student2@gmail.com",
                "2024002", "Maria Santos", "09198765432", "Information Technology");
        ensureSampleStudent(
                "student3", "student123", "edulibrary67+student3@gmail.com",
                "2024003", "Pedro Reyes", "09234567890", "Engineering");

        ensureSampleCatalog();

        ensureFinePerDayForExistingBooks();

        ensureQrPayloadsForExistingBooks();

        ensureSampleEbooks();
    }

    private void ensureSampleStudent(
            String username,
            String rawPassword,
            String email,
            String studentId,
            String fullName,
            String phone,
            String course) {
        if (studentProfileRepository.findByUserUsername(username).isPresent()
                || studentProfileRepository.findByStudentId(studentId).isPresent()) {
            log.debug("Sample student '{}' or id '{}' already has a library profile; skipping.", username, studentId);
            return;
        }

        User student = userRepository.findByUsername(username).orElseGet(() -> {
            User newStudent = new User();
            newStudent.setUsername(username);
            newStudent.setPassword(passwordEncoder.encode(rawPassword));
            newStudent.setEmail(email);
            newStudent.setRole(UserRole.STUDENT);
            newStudent.setEnabled(true);
            User saved = userRepository.save(newStudent);
            log.info("Sample student user created: username={} password={}", username, rawPassword);
            return saved;
        });

        if (studentProfileRepository.findByUserId(student.getId()).isPresent()) {
            log.debug("Library profile already linked to user '{}'; skipping.", username);
            return;
        }

        StudentProfile profile = new StudentProfile();
        profile.setUser(student);
        profile.setStudentId(studentId);
        profile.setFullName(fullName);
        profile.setPhone(phone);
        profile.setCourse(course);
        studentProfileRepository.save(profile);

        log.info("Library profile ensured for sample student '{}'.", username);
    }

    private void ensureSampleCatalog() {
        Map<String, Category> categoriesByName = new HashMap<>();
        for (Category c : categoryRepository.findAll()) {
            categoriesByName.put(c.getName().trim().toLowerCase(), c);
        }

        Map<String, Author> authorsByName = new HashMap<>();
        for (Author a : authorRepository.findAll()) {
            authorsByName.put(a.getName().trim().toLowerCase(), a);
        }

        Category programming = ensureCategory(categoriesByName, "Programming");
        Category sciFi = ensureCategory(categoriesByName, "Science Fiction");
        Category reference = ensureCategory(categoriesByName, "Reference");
        Category softwareEngineering = ensureCategory(categoriesByName, "Software Engineering");
        Category webDevelopment = ensureCategory(categoriesByName, "Web Development");
        Category dataScience = ensureCategory(categoriesByName, "Data Science");

        Author joshBloch = ensureAuthor(authorsByName, "Joshua Bloch");
        Author robertMartin = ensureAuthor(authorsByName, "Robert C. Martin");
        Author asimov = ensureAuthor(authorsByName, "Isaac Asimov");
        Author kathySierra = ensureAuthor(authorsByName, "Kathy Sierra");
        Author ericFreeman = ensureAuthor(authorsByName, "Eric Freeman");
        Author martinFowler = ensureAuthor(authorsByName, "Martin Fowler");
        Author andrewHunt = ensureAuthor(authorsByName, "Andrew Hunt");
        Author davidThomas = ensureAuthor(authorsByName, "David Thomas");
        Author steveMcConnell = ensureAuthor(authorsByName, "Steve McConnell");
        Author yanLecun = ensureAuthor(authorsByName, "Ian Goodfellow");

        List<BookSeed> seeds = new ArrayList<>();
        seeds.add(new BookSeed("Effective Java (3rd Edition)", "9780134685991", "B-00001", programming, joshBloch, 5, bd("10.00")));
        seeds.add(new BookSeed("Clean Code", "9780132350884", "B-00002", softwareEngineering, robertMartin, 6, bd("12.00")));
        seeds.add(new BookSeed("Foundation", "9780553293357", "B-00003", sciFi, asimov, 4, bd("5.00")));
        seeds.add(new BookSeed("Data Structures and Algorithms in Java", "9780672324536", "B-00004", reference, kathySierra, 4, bd("15.00")));
        seeds.add(new BookSeed("Head First Java", "9780596009205", "B-00005", programming, kathySierra, 8, bd("8.00")));
        seeds.add(new BookSeed("Head First Design Patterns", "9780596007126", "B-00006", softwareEngineering, ericFreeman, 5, bd("10.00")));
        seeds.add(new BookSeed("Refactoring", "9780134757599", "B-00007", softwareEngineering, martinFowler, 5, bd("12.00")));
        seeds.add(new BookSeed("The Pragmatic Programmer", "9780201616224", "B-00008", softwareEngineering, andrewHunt, 5, bd("10.00")));
        seeds.add(new BookSeed("Code Complete", "9780735619678", "B-00009", softwareEngineering, steveMcConnell, 4, bd("12.00")));
        seeds.add(new BookSeed("Java Concurrency in Practice", "9780321349606", "B-00010", programming, robertMartin, 3, bd("15.00")));
        seeds.add(new BookSeed("Spring in Action", "9781617294945", "B-00011", webDevelopment, joshBloch, 4, bd("10.00")));
        seeds.add(new BookSeed("Learning SQL", "9780596520830", "B-00012", reference, davidThomas, 6, bd("15.00")));
        seeds.add(new BookSeed("Clean Architecture", "9780134494166", "B-00013", softwareEngineering, robertMartin, 5, bd("18.00")));
        seeds.add(new BookSeed("Design Patterns", "9780201633610", "B-00014", softwareEngineering, ericFreeman, 5, bd("18.00")));
        seeds.add(new BookSeed("Deep Learning", "9780262035613", "B-00015", dataScience, yanLecun, 3, bd("25.00")));

        List<Book> existingBooks = bookRepository.findAll();
        Map<String, Book> booksByTitle = new HashMap<>();
        Map<String, Book> booksByBarcode = new HashMap<>();
        for (Book b : existingBooks) {
            booksByTitle.put(b.getTitle().trim().toLowerCase(), b);
            if (b.getBarcode() != null) {
                booksByBarcode.put(b.getBarcode().trim().toUpperCase(), b);
            }
        }

        int inserted = 0;
        for (BookSeed seed : seeds) {
            if (booksByTitle.containsKey(seed.title().trim().toLowerCase())) {
                continue;
            }
            if (booksByBarcode.containsKey(seed.barcode().trim().toUpperCase())) {
                continue;
            }
            Book book = new Book();
            book.setTitle(seed.title());
            book.setIsbn(seed.isbn());
            book.setBarcode(seed.barcode());
            book.setCategory(seed.category());
            book.setAuthor(seed.author());
            book.setTotalCopies(seed.totalCopies());
            book.setAvailableCopies(seed.totalCopies());
            book.setFinePerDay(seed.finePerDay());
            bookRepository.save(book);
            inserted++;
        }

        if (inserted > 0) {
            log.info("Inserted {} additional sample books for a richer library catalog.", inserted);
        }
    }

    private void ensureFinePerDayForExistingBooks() {
        int updated = 0;
        for (Book book : bookRepository.findAll()) {
            if (book.getFinePerDay() != null) {
                continue;
            }
            book.setFinePerDay(suggestFinePerDay(book.getTitle()));
            bookRepository.save(book);
            updated++;
        }
        if (updated > 0) {
            log.info("Assigned per-book overdue fines for {} existing book(s).", updated);
        }
    }

    private static BigDecimal suggestFinePerDay(String title) {
        String lower = title == null ? "" : title.toLowerCase();
        if (lower.contains("deep learning")) {
            return bd("25.00");
        }
        if (lower.contains("architecture") || lower.contains("design patterns")) {
            return bd("18.00");
        }
        if (lower.contains("reference") || lower.contains("sql") || lower.contains("concurrency")) {
            return bd("15.00");
        }
        if (lower.contains("foundation") || lower.contains("fiction")) {
            return bd("5.00");
        }
        if (lower.contains("clean code") || lower.contains("code complete") || lower.contains("refactoring")) {
            return bd("12.00");
        }
        return bd("10.00");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private Category ensureCategory(Map<String, Category> categoriesByName, String name) {
        String key = name.trim().toLowerCase();
        if (categoriesByName.containsKey(key)) {
            return categoriesByName.get(key);
        }
        Category category = new Category();
        category.setName(name);
        category = categoryRepository.save(category);
        categoriesByName.put(key, category);
        return category;
    }

    private Author ensureAuthor(Map<String, Author> authorsByName, String name) {
        String key = name.trim().toLowerCase();
        if (authorsByName.containsKey(key)) {
            return authorsByName.get(key);
        }
        Author author = new Author();
        author.setName(name);
        author = authorRepository.save(author);
        authorsByName.put(key, author);
        return author;
    }

    private void ensureQrPayloadsForExistingBooks() {
        List<Book> booksWithoutQr = bookRepository.findAll().stream()
                .filter(b -> b.getQrPayload() == null || b.getQrPayload().isEmpty())
                .toList();

        if (!booksWithoutQr.isEmpty()) {
            for (Book book : booksWithoutQr) {
                String payload = "BOOK:" + book.getId();
                book.setQrPayload(payload);
                bookRepository.save(book);
            }
            log.info("Generated QR payloads for {} existing books.", booksWithoutQr.size());
        }
    }

    private void ensureSampleEbooks() {
        if (bookRepository.count() == 0) {
            return;
        }
        try {
            Path uploadDir = Path.of(libraryProperties.getUploadDir());
            Files.createDirectories(uploadDir);

            List<Book> seedBooks = bookRepository.findAll().stream()
                    .sorted(Comparator.comparing(Book::getId))
                    .toList();
            for (Book book : seedBooks) {
                String safeTitle = book.getTitle().replaceAll("[^a-zA-Z0-9]+", "_");
                String filename = "sample_" + book.getId() + "_" + safeTitle + ".pdf";
                Path pdfPath = uploadDir.resolve(filename).toAbsolutePath();
                writeSamplePdf(pdfPath, book);

                EbookAsset asset = ebookAssetRepository.findByBook_Id(book.getId()).orElseGet(EbookAsset::new);
                asset.setBook(book);
                asset.setOriginalFilename(book.getTitle() + ".pdf");
                asset.setStoredPath(pdfPath.toString());
                ebookAssetRepository.save(asset);
            }
            log.info("Sample digital library assets created.");
        } catch (IOException e) {
            log.warn("Could not create sample e-books: {}", e.getMessage());
        }
    }

    private void writeSamplePdf(Path path, Book book) throws IOException {
        try (PDDocument document = new PDDocument()) {
            final float margin = 50f;
            final float pageHeight = PDRectangle.A4.getHeight();
            final float usableWidth = PDRectangle.A4.getWidth() - (margin * 2);
            final float normalSize = 12f;
            final float headingSize = 15f;
            final float lineGap = 17f;
            final float paragraphGap = 11f;

            List<String> sections = buildSampleSections(book.getTitle());
            List<String> codeSamples = buildCodeSamples(book.getTitle());

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = pageHeight - margin;

            y = writeLine(content, "Smart Library - Sample Study Edition", margin, y, PDType1Font.HELVETICA_BOLD, 20f, lineGap);
            y = writeLine(content, "Title: " + safePdfLine(book.getTitle(), 100), margin, y, PDType1Font.HELVETICA_BOLD, 13f, lineGap);
            y = writeLine(content, "This is expanded reading material for practice and review.", margin, y, PDType1Font.HELVETICA, 11f, lineGap);
            y -= 10f;

            for (int i = 0; i < sections.size(); i++) {
                String heading = "Section " + (i + 1);
                if (y < margin + 90f) {
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = pageHeight - margin;
                }
                y = writeLine(content, heading, margin, y, PDType1Font.HELVETICA_BOLD, headingSize, lineGap);

                List<String> wrapped = wrapText(sections.get(i), PDType1Font.HELVETICA, normalSize, usableWidth);
                for (String line : wrapped) {
                    if (y < margin + 40f) {
                        content.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        y = pageHeight - margin;
                    }
                    y = writeLine(content, line, margin, y, PDType1Font.HELVETICA, normalSize, lineGap);
                }
                y -= paragraphGap;
            }

            if (!codeSamples.isEmpty()) {
                if (y < margin + 140f) {
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = pageHeight - margin;
                }
                y = writeLine(content, "Code Examples", margin, y, PDType1Font.HELVETICA_BOLD, headingSize, lineGap);
                y = writeLine(content, "These are sample snippets included for study.", margin, y, PDType1Font.HELVETICA, 11f, lineGap);
                y -= 6f;

                for (String snippet : codeSamples) {
                    List<String> wrappedCode = wrapText(snippet, PDType1Font.COURIER, 10.5f, usableWidth);
                    for (String codeLine : wrappedCode) {
                        if (y < margin + 35f) {
                            content.close();
                            page = new PDPage(PDRectangle.A4);
                            document.addPage(page);
                            content = new PDPageContentStream(document, page);
                            y = pageHeight - margin;
                        }
                        y = writeLine(content, codeLine, margin, y, PDType1Font.COURIER, 10.5f, 14.5f);
                    }
                    y -= 8f;
                }
            }

            if (y < margin + 50f) {
                content.close();
                page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                content = new PDPageContentStream(document, page);
                y = pageHeight - margin;
            }

            writeLine(content,
                    "End of sample e-book. You can replace this with your own full PDF upload.",
                    margin,
                    y,
                    PDType1Font.HELVETICA_OBLIQUE,
                    11f,
                    lineGap);
            content.close();
            document.save(path.toFile());
        }
    }

    private float writeLine(
            PDPageContentStream content,
            String text,
            float x,
            float y,
            org.apache.pdfbox.pdmodel.font.PDFont font,
            float fontSize,
            float stepDown) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - stepDown;
    }

    private List<String> wrapText(
            String text,
            org.apache.pdfbox.pdmodel.font.PDFont font,
            float fontSize,
            float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            float width = font.getStringWidth(candidate) / 1000f * fontSize;
            if (width <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                } else {
                    lines.add(word);
                }
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private List<String> buildSampleSections(String title) {
        return Arrays.asList(
                "Overview. This reading material introduces core ideas related to " + title + ". "
                        + "Focus on understanding concepts before memorizing details. "
                        + "When studying, write short notes using your own words and identify real-life examples.",
                "Key Principles. Good practice starts with clarity, consistency, and feedback. "
                        + "Break large tasks into smaller parts, test each part, and improve continuously. "
                        + "Always compare at least two approaches before deciding on a final solution.",
                "Practical Workflow. Start with planning, continue with implementation, and end with verification. "
                        + "Use small checkpoints so you can detect mistakes early. "
                        + "Document assumptions and keep a log of decisions to make review easier.",
                "Common Mistakes. Many learners skip fundamentals and jump directly to advanced topics. "
                        + "Another common issue is solving problems once without reviewing why the solution works. "
                        + "To avoid this, revisit examples and explain the result out loud.",
                "Applied Scenario. Imagine building a project that must be maintainable for years. "
                        + "Use readable naming, modular structure, and incremental testing. "
                        + "Evaluate performance and correctness before adding new features.",
                "Review Questions. What is the main objective of this topic? "
                        + "Which step in your workflow gives the highest impact on quality? "
                        + "How would you improve a weak implementation using the principles above?",
                "Study Exercise. Create a short implementation task based on " + title + ". "
                        + "Define acceptance criteria, implement the solution, then perform self-review. "
                        + "Write one paragraph on what you would do differently in a second iteration.",
                "Architecture Perspective. Every robust system balances readability, scalability, and maintainability. "
                        + "A good architecture makes future changes cheaper, allows safe refactoring, and supports testing from day one. "
                        + "Use clear layers and avoid mixing business logic with UI-specific behavior.",
                "Data Modeling Notes. Start by listing entities, relationships, and constraints before writing implementation details. "
                        + "Identify which fields are required, which can be nullable, and what validations must always run. "
                        + "This reduces bugs that usually appear when data grows in real projects.",
                "Validation Strategy. Validate input both on the client and on the server. "
                        + "Client-side checks improve user experience, while server-side checks protect data integrity and security. "
                        + "Never trust unchecked values from forms or external services.",
                "Error Handling. Prefer meaningful error messages that explain what failed and how to fix it. "
                        + "Log technical details for developers, but keep user-facing messages simple and actionable. "
                        + "Consistent error handling improves reliability and maintainability.",
                "Performance Basics. Measure first before optimizing. "
                        + "Review expensive loops, repeated database queries, and unnecessary object creation. "
                        + "Small improvements in hot paths can significantly reduce response time.",
                "Testing Fundamentals. Cover critical business rules with unit tests and integration tests. "
                        + "A healthy test suite protects against regressions and enables faster refactoring. "
                        + "Treat failing tests as useful feedback, not as blockers.",
                "Collaboration Practices. Use meaningful commit messages, code reviews, and documented decisions. "
                        + "Good teamwork reduces misunderstandings and keeps project quality consistent across contributors. "
                        + "Explain the why behind changes, not only the what.",
                "Security Awareness. Protect user data by validating input, controlling access, and avoiding sensitive data leaks. "
                        + "Review authentication and authorization flows regularly. "
                        + "Security should be continuous practice, not a one-time checklist.",
                "Debugging Playbook. Reproduce the issue first, isolate the failing component, and inspect logs with clear hypotheses. "
                        + "Change one variable at a time to avoid false conclusions. "
                        + "Document the root cause and prevention steps after each fix.",
                "Deployment Mindset. Prepare your project for real usage by handling configuration, environment differences, and monitoring. "
                        + "A deployable application is not only feature-complete; it is also observable and recoverable. "
                        + "Always plan rollback and backup strategies.",
                "Maintenance Guide. Software quality declines when improvements stop. "
                        + "Schedule periodic refactoring, dependency updates, and performance reviews. "
                        + "Long-term maintainability is a major part of professional development work."
        );
    }

    private List<String> buildCodeSamples(String title) {
        String lower = title == null ? "" : title.toLowerCase();
        if (lower.contains("java") || lower.contains("clean") || lower.contains("code") || lower.contains("spring")) {
            return Arrays.asList(
                    "public class BookService {\n"
                            + "    public List<Book> availableBooks(List<Book> books) {\n"
                            + "        return books.stream()\n"
                            + "            .filter(Book::isAvailable)\n"
                            + "            .sorted(Comparator.comparing(Book::getTitle))\n"
                            + "            .toList();\n"
                            + "    }\n"
                            + "}",
                    "@GetMapping(\"/student/recommendations\")\n"
                            + "public ResponseEntity<List<String>> recommendations() {\n"
                            + "    List<String> titles = bookService.search(null, null, null, true)\n"
                            + "        .stream()\n"
                            + "        .limit(5)\n"
                            + "        .map(Book::getTitle)\n"
                            + "        .toList();\n"
                            + "    return ResponseEntity.ok(titles);\n"
                            + "}"
            );
        }

        if (lower.contains("sql") || lower.contains("data")) {
            return Arrays.asList(
                    "SELECT b.title, b.available_copies, c.name AS category\n"
                            + "FROM books b\n"
                            + "LEFT JOIN categories c ON c.id = b.category_id\n"
                            + "WHERE b.available_copies > 0\n"
                            + "ORDER BY b.title ASC;",
                    "SELECT s.student_id, COUNT(*) AS borrowed_count\n"
                            + "FROM book_issues bi\n"
                            + "JOIN student_profiles s ON s.id = bi.student_id\n"
                            + "WHERE bi.status = 'BORROWED'\n"
                            + "GROUP BY s.student_id;"
            );
        }

        return Arrays.asList(
                "function recommendBooks(books) {\n"
                        + "  return books\n"
                        + "    .filter(book => book.availableCopies > 0)\n"
                        + "    .sort((a, b) => a.title.localeCompare(b.title))\n"
                        + "    .slice(0, 5);\n"
                        + "}",
                "const summary = {\n"
                        + "  totalBooks: 120,\n"
                        + "  activeLoans: 37,\n"
                        + "  overdue: 5\n"
                        + "};"
        );
    }

    private void ensureAdminContactEmails() {
        userRepository.findAll().stream()
                .filter(u -> u.getRole() == com.smartlibrary.model.UserRole.ADMIN)
                .forEach(admin -> {
                    String email = admin.getEmail();
                    if (email == null || email.isBlank()
                            || email.endsWith("@admin.local")
                            || email.endsWith("@library.local")
                            || "resurreccionjeremy9@gmail.com".equalsIgnoreCase(email.trim())) {
                        admin.setEmail("mercadocarlo645@gmail.com");
                        userRepository.save(admin);
                        log.info("Set admin {} contact email to mercadocarlo645@gmail.com", admin.getUsername());
                    }
                });
    }

    private void fixInvalidEmails() {
        List<User> users = userRepository.findAll();
        int fixed = 0;
        for (User user : users) {
            String email = user.getEmail();
            if (email == null || email.isBlank()) {
                continue;
            }
            email = email.trim().toLowerCase();
            String fixedEmail = null;
            if (!email.contains("@")) {
                fixedEmail = "edulibrary67+" + email + "@gmail.com";
            } else if (email.endsWith("@library.local")) {
                String name = email.substring(0, email.indexOf("@"));
                fixedEmail = "edulibrary67+" + name + "@gmail.com";
            }
            if (fixedEmail != null && !fixedEmail.equalsIgnoreCase(user.getEmail())) {
                user.setEmail(fixedEmail);
                userRepository.save(user);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("Fixed {} user email(s) to Gmail + alias format", fixed);
        }
    }

    private void fixSchema() {
        try {
            repairStudentProfileUserForeignKey();
            relaxBooksTotalCopiesConstraint();
            jdbcTemplate.execute("ALTER TABLE library.ebooks DROP COLUMN IF EXISTS uploaded_at");
            relaxTimestampColumn("admin_revenue", "created_at");
            relaxTimestampColumn("admin_revenue", "updated_at");
            relaxTimestampColumn("reservations", "created_at");
            relaxTimestampColumn("reservations", "updated_at");
            relaxTimestampColumn("ebooks", "created_at");
            relaxTimestampColumn("ebooks", "updated_at");
            relaxTimestampColumn("otp_codes", "created_at");
            relaxTimestampColumn("otp_codes", "updated_at");
            jdbcTemplate.execute(
                    "ALTER TABLE library.student_profiles ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT false");
            log.debug("Schema fixes applied successfully.");
        } catch (Exception e) {
            log.warn("Schema fix skipped (possibly table does not exist yet): {}", e.getMessage());
        }
    }

    private void relaxTimestampColumn(String table, String column) {
        jdbcTemplate.execute(
                "ALTER TABLE " + LIBRARY_SCHEMA + "." + table + " ALTER COLUMN " + column + " TYPE TIMESTAMP(6)");
    }

    /** Allow total_copies = 0 so books can be listed for reservation queue only. */
    private void relaxBooksTotalCopiesConstraint() {
        try {
            List<String> constraintNames = jdbcTemplate.queryForList("""
                    SELECT c.conname
                    FROM pg_constraint c
                    JOIN pg_class t ON c.conrelid = t.oid
                    JOIN pg_namespace n ON t.relnamespace = n.oid
                    WHERE n.nspname = 'library'
                      AND t.relname = 'books'
                      AND c.contype = 'c'
                      AND pg_get_constraintdef(c.oid) ILIKE '%total_copies%'
                    """, String.class);
            for (String name : constraintNames) {
                jdbcTemplate.execute("ALTER TABLE library.books DROP CONSTRAINT IF EXISTS \"" + name + "\"");
            }
            jdbcTemplate.execute(
                    "ALTER TABLE library.books ADD CONSTRAINT books_total_copies_check CHECK (total_copies >= 0)");
            log.info("Relaxed library.books total_copies check to allow 0 (reservation-only catalog entries).");
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.debug("library.books total_copies check already allows 0.");
                return;
            }
            log.warn("Could not relax library.books total_copies constraint: {}", e.getMessage());
        }
    }

    private void repairStudentProfileUserForeignKey() {
        try {
            jdbcTemplate.update("""
                    UPDATE library.student_profiles sp
                    SET user_id = s.user_id
                    FROM public.students s
                    WHERE sp.user_id NOT IN (SELECT id FROM public.users)
                      AND LOWER(s.student_number) = LOWER(sp.student_id)
                      AND s.user_id IS NOT NULL
                    """);

            jdbcTemplate.update("""
                    UPDATE library.student_profiles sp
                    SET user_id = pu.id
                    FROM public.users pu
                    WHERE sp.user_id NOT IN (SELECT id FROM public.users)
                      AND LOWER(TRIM(pu.email)) IN (
                          SELECT LOWER(TRIM(s.email))
                          FROM public.students s
                          WHERE LOWER(s.student_number) = LOWER(sp.student_id)
                            AND s.email IS NOT NULL
                            AND TRIM(s.email) <> ''
                      )
                    """);

            int removed = jdbcTemplate.update("""
                    DELETE FROM library.student_profiles
                    WHERE user_id NOT IN (SELECT id FROM public.users)
                    """);
            if (removed > 0) {
                log.warn("Removed {} orphan library.student_profiles row(s) with invalid user_id", removed);
            }

            List<String> constraints = jdbcTemplate.queryForList("""
                    SELECT c.conname
                    FROM pg_constraint c
                    JOIN pg_class t ON t.oid = c.conrelid
                    JOIN pg_namespace n ON n.oid = t.relnamespace
                    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
                    WHERE n.nspname = 'library'
                      AND t.relname = 'student_profiles'
                      AND c.contype = 'f'
                      AND a.attname = 'user_id'
                    """, String.class);

            for (String constraintName : constraints) {
                jdbcTemplate.execute("ALTER TABLE library.student_profiles DROP CONSTRAINT IF EXISTS \""
                        + constraintName.replace("\"", "") + "\"");
            }

            jdbcTemplate.execute("""
                    ALTER TABLE library.student_profiles
                    ADD CONSTRAINT fk_student_profiles_user
                    FOREIGN KEY (user_id) REFERENCES public.users (id)
                    """);
            log.info("Repaired library.student_profiles.user_id foreign key -> public.users");
        } catch (Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("already exists")) {
                log.debug("library.student_profiles FK already points to public.users");
            } else {
                log.warn("Could not repair library.student_profiles FK: {}", message);
            }
        }
    }

    private void fixNullVersions() {
        try {
            int booksFixed = jdbcTemplate.update("UPDATE library.books SET version = 0 WHERE version IS NULL");
            int usersFixed = jdbcTemplate.update("UPDATE public.users SET version = 0 WHERE version IS NULL");
            int categoriesFixed = jdbcTemplate.update("UPDATE library.categories SET version = 0 WHERE version IS NULL");
            int authorsFixed = jdbcTemplate.update("UPDATE library.authors SET version = 0 WHERE version IS NULL");
            int studentProfilesFixed = jdbcTemplate.update("UPDATE library.student_profiles SET version = 0 WHERE version IS NULL");
            int bookIssuesFixed = jdbcTemplate.update("UPDATE library.book_issues SET version = 0 WHERE version IS NULL");
            int reservationsFixed = jdbcTemplate.update("UPDATE library.reservations SET version = 0 WHERE version IS NULL");
            int adminRevenueFixed = jdbcTemplate.update("UPDATE library.admin_revenue SET version = 0 WHERE version IS NULL");
            int ebooksFixed = jdbcTemplate.update("UPDATE library.ebooks SET version = 0 WHERE version IS NULL");
            int otpCodesFixed = jdbcTemplate.update("UPDATE library.otp_codes SET version = 0 WHERE version IS NULL");
            int total = booksFixed + usersFixed + categoriesFixed + authorsFixed + studentProfilesFixed + bookIssuesFixed
                    + reservationsFixed + adminRevenueFixed + ebooksFixed + otpCodesFixed;
            if (total > 0) {
                log.info("Fixed {} row(s) with NULL version value(s).", total);
            }
        } catch (Exception e) {
            log.warn("Could not fix NULL version values (possibly table does not exist yet): {}", e.getMessage());
        }
    }

    private String safePdfLine(String value, int maxLength) {
        String text = value == null ? "" : value;
        text = text.replaceAll("[\\r\\n\\t]", " ").trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private record BookSeed(
            String title,
            String isbn,
            String barcode,
            Category category,
            Author author,
            int totalCopies,
            BigDecimal finePerDay
    ) {
    }
}
