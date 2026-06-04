package com.honortech.dataplatform.subject.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.honortech.dataplatform.subject.entity.Subject;
import com.honortech.dataplatform.subject.mapper.SubjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SubjectServiceImpl implements SubjectService {

    private final SubjectMapper subjectMapper;

    public SubjectServiceImpl(SubjectMapper subjectMapper) {
        this.subjectMapper = subjectMapper;
    }

    @Override
    public Subject resolveSubject(String subjectCode, String subjectName) {
        if (subjectCode != null && !subjectCode.isBlank()) {
            Subject existing = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>()
                    .eq(Subject::getSubjectCode, subjectCode.trim()));
            if (existing != null) {
                if ((existing.getSubjectName() == null || existing.getSubjectName().isBlank())
                        && subjectName != null && !subjectName.isBlank()) {
                    existing.setSubjectName(subjectName.trim());
                    existing.setUpdatedAt(LocalDateTime.now());
                    subjectMapper.updateById(existing);
                }
                return existing;
            }
        }

        Subject subject = new Subject();
        subject.setSubjectCode(subjectCode == null || subjectCode.isBlank() ? generateSubjectCode() : subjectCode.trim());
        subject.setSubjectName(subjectName == null || subjectName.isBlank() ? subject.getSubjectCode() : subjectName.trim());
        subject.setStatus("ACTIVE");
        subject.setCreatedAt(LocalDateTime.now());
        subject.setUpdatedAt(LocalDateTime.now());
        subjectMapper.insert(subject);
        return subject;
    }

    @Override
    public Subject findById(Long subjectId) {
        return subjectId == null ? null : subjectMapper.selectById(subjectId);
    }

    private String generateSubjectCode() {
        return "SUBJ-" + System.currentTimeMillis();
    }
}
