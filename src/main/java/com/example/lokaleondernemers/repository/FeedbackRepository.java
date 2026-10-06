package com.example.lokaleondernemers.repository;

import com.example.lokaleondernemers.model.Feedback;
import com.example.lokaleondernemers.model.Onderneming;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByOndernemingOrderByAangemaaktOpDesc(Onderneming onderneming);

    List<Feedback> findAllByOrderByAangemaaktOpDesc();

    List<Feedback> findByStatusInOrderByAangemaaktOpDesc(Collection<Feedback.Status> statussen);

    long countByStatus(Feedback.Status status);
}
