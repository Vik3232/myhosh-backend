package com.myhosh.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SiteReviewRepository extends JpaRepository<SiteReview, Long> {
    List<SiteReview> findAllByOrderByCreatedAtDesc();
}