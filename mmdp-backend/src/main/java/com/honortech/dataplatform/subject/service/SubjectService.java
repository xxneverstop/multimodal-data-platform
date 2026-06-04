package com.honortech.dataplatform.subject.service;

import com.honortech.dataplatform.subject.entity.Subject;

public interface SubjectService {

    Subject resolveSubject(String subjectCode, String subjectName);

    Subject findById(Long subjectId);
}
