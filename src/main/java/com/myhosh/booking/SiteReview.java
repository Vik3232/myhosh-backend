package com.myhosh.booking;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "site_reviews")
public class SiteReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String guestName;

    @Column(nullable = false)
    private int rating;

    @Column(length = 80)
    private String foodHighlight;

    @Column(nullable = false, length = 1500)
    private String comment;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getFoodHighlight() { return foodHighlight; }
    public void setFoodHighlight(String foodHighlight) { this.foodHighlight = foodHighlight; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}