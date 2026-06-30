package com.smartlibrary.config;

import com.smartlibrary.entity.BookIssue;
import com.smartlibrary.model.IssueStatus;
import com.smartlibrary.repository.BookIssueRepository;
import com.smartlibrary.service.BookIssueService;
import com.smartlibrary.service.MailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ScheduledLibraryJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledLibraryJobs.class);

    private final BookIssueService bookIssueService;
    private final BookIssueRepository bookIssueRepository;
    private final MailNotificationService mailNotificationService;
    private final LibraryProperties libraryProperties;

    public ScheduledLibraryJobs(
            BookIssueService bookIssueService,
            BookIssueRepository bookIssueRepository,
            MailNotificationService mailNotificationService,
            LibraryProperties libraryProperties) {
        this.bookIssueService = bookIssueService;
        this.bookIssueRepository = bookIssueRepository;
        this.mailNotificationService = mailNotificationService;
        this.libraryProperties = libraryProperties;
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void markOverdueDaily() {
        int n = bookIssueService.markOverdue();
        if (n > 0) {
            log.info("Marked {} issues as overdue", n);
        }
    }

    @Transactional(readOnly = true)
    @Scheduled(cron = "0 30 8 * * *")
    public void dueDateReminders() {
        LocalDate dueDate = LocalDate.now().plusDays(libraryProperties.getReminderDaysBeforeDue());
        LocalDateTime start = dueDate.atStartOfDay();
        LocalDateTime end = dueDate.plusDays(1).atStartOfDay();
        List<BookIssue> list = bookIssueRepository.findDueOnDate(IssueStatus.BORROWED, start, end);
        for (BookIssue bi : list) {
            mailNotificationService.sendDueReminder(bi);
        }
        if (!list.isEmpty()) {
            log.info("Sent {} due-date reminder(s)", list.size());
        }
    }
}
