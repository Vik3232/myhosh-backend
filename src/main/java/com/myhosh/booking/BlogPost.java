
package com.myhosh.booking;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "blog_posts")
public class BlogPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 80)
    private String category = "Kitchen & Stories";

    @Column(columnDefinition = "TEXT")
    private String content;

    @JsonIgnore
    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrlsRaw;

    @Column(length = 100)
    private String author;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @JsonProperty("imageUrls")
    public List<String> getImageUrls() {
        if (imageUrlsRaw == null || imageUrlsRaw.isBlank()) return Collections.emptyList();
        return Arrays.stream(imageUrlsRaw.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @JsonProperty("imageUrls")
    public void setImageUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            this.imageUrlsRaw = "";
        } else {
            this.imageUrlsRaw = String.join("\n",
                    urls.stream()
                            .filter(u -> u != null && u.trim().startsWith("https://") && u.trim().length() <= 1000)
                            .map(String::trim)
                            .toList());
        }
    }
}