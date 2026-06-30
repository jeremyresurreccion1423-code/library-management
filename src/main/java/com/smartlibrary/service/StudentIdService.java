package com.smartlibrary.service;

import com.smartlibrary.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StudentIdService {

    private static final Logger logger = LoggerFactory.getLogger(StudentIdService.class);
    private static final String INSTITUTION_CODE = "101";
    private static final String ID_FORMAT = "%s-%03d";

    private final StudentProfileRepository studentProfileRepository;

    public StudentIdService(StudentProfileRepository studentProfileRepository) {
        this.studentProfileRepository = studentProfileRepository;
    }

    public String generateNextStudentId() {
        long count = studentProfileRepository.count();
        String studentId = String.format(ID_FORMAT, INSTITUTION_CODE, count);
        logger.debug("Generated student ID: {} (count: {})", studentId, count);
        return studentId;
    }
}
