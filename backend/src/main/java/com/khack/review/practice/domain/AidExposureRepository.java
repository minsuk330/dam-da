package com.khack.review.practice.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AidExposureRepository extends JpaRepository<AidExposure, Long> {

    List<AidExposure> findByPresentationIdOrderByExposedAtAscIdAsc(Long presentationId);
}
