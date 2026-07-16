package com.smartlibrary.web.student;

import com.smartlibrary.repository.BookIssueRepository;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.StudentProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StudentDashboardPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private BookIssueRepository bookIssueRepository;

    @Autowired
    private BookRepository bookRepository;

    @Test
    @WithUserDetails("student1")
    void studentDashboardRenders() throws Exception {
        var profile = studentProfileRepository.findByUserUsername("student1").orElseThrow();
        bookIssueRepository.findByStudent_IdOrderByIssuedAtDesc(profile.getId());
        bookRepository.findDigitalBooksBorrowedByStudent(profile.getId());

        mockMvc.perform(get("/student"))
                .andExpect(status().isOk());
    }
}
