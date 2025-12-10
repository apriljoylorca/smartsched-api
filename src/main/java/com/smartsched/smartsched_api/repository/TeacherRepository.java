package com.smartsched.smartsched_api.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.smartsched.smartsched_api.model.Teacher;

@Repository
public interface TeacherRepository extends MongoRepository<Teacher, String> {
}
