package com.myhosh.booking;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/blog")
public class BlogController {

    private final BlogPostRepository blogPostRepository;

    public BlogController(BlogPostRepository blogPostRepository) {
        this.blogPostRepository = blogPostRepository;
    }

    @GetMapping
    public List<BlogPost> getAllPosts() {
        return blogPostRepository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping
    public ResponseEntity<?> createPost(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @RequestBody BlogPost post) {

        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Only ADMIN can publish blog posts or photos.");
        }
        if (post.getTitle() == null || post.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body("Post title is required.");
        }

        post.setTitle(post.getTitle().trim());
        post.setCategory((post.getCategory() == null || post.getCategory().isBlank()) ? "Gallery & Stories" : post.getCategory().trim());
        post.setAuthor(StaffController.getSessionUsername(token));
        post.setCreatedAt(Instant.now());
        return ResponseEntity.ok(blogPostRepository.save(post));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePost(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody BlogPost updatedData) {

        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Only ADMIN can edit blog posts or photos.");
        }
        if (updatedData.getTitle() == null || updatedData.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body("Post title is required.");
        }

        Optional<BlogPost> existingOpt = blogPostRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BlogPost existing = existingOpt.get();
        existing.setTitle(updatedData.getTitle().trim());
        existing.setCategory((updatedData.getCategory() == null || updatedData.getCategory().isBlank())
                ? existing.getCategory()
                : updatedData.getCategory().trim());
        existing.setContent(updatedData.getContent() != null ? updatedData.getContent() : "");
        existing.setImageUrls(updatedData.getImageUrls());

        return ResponseEntity.ok(blogPostRepository.save(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id) {

        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Only ADMIN can delete blog posts.");
        }
        if (!blogPostRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        blogPostRepository.deleteById(id);
        return ResponseEntity.ok("Blog post deleted.");
    }
}