package com.myhosh.booking;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuItemRepository menuItemRepository;
    private final MenuPriceHistoryRepository priceHistoryRepository;

    public MenuController(MenuItemRepository menuItemRepository,
                          MenuPriceHistoryRepository priceHistoryRepository) {
        this.menuItemRepository = menuItemRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @GetMapping
    public List<MenuItem> getAllMenuItems() {
        return menuItemRepository.findAll().stream()
                .sorted(Comparator.comparingInt(MenuItem::getSortOrder).thenComparing(MenuItem::getId))
                .toList();
    }

    @GetMapping("/price-history")
    public ResponseEntity<?> getPriceHistory(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: ADMIN access required.");
        }
        List<MenuPriceHistory> history = priceHistoryRepository.findAll(Sort.by(Sort.Direction.DESC, "changedAt"));
        return ResponseEntity.ok(history);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateMenuItem(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Only ADMIN can edit menu items or prices.");
        }

        Optional<MenuItem> opt = menuItemRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MenuItem item = opt.get();
        BigDecimal oldPrice = item.getPrice().setScale(2, RoundingMode.HALF_UP);

        if (body.containsKey("name")) {
            String newName = String.valueOf(body.get("name")).trim();
            if (newName.isEmpty()) return ResponseEntity.badRequest().body("Dish name cannot be empty.");
            item.setName(newName);
        }
        if (body.containsKey("category")) {
            String newCat = String.valueOf(body.get("category")).trim();
            if (newCat.isEmpty()) return ResponseEntity.badRequest().body("Category cannot be empty.");
            item.setCategory(newCat);
        }
        if (body.containsKey("description")) {
            item.setDescription(body.get("description") != null ? String.valueOf(body.get("description")).trim() : "");
        }
        if (body.containsKey("dietaryTags")) {
            item.setDietaryTags(body.get("dietaryTags") != null ? String.valueOf(body.get("dietaryTags")).trim() : "");
        }
        if (body.containsKey("available")) {
            item.setAvailable(Boolean.parseBoolean(String.valueOf(body.get("available"))));
        }

        BigDecimal newPrice = oldPrice;
        if (body.containsKey("price")) {
            try {
                newPrice = new BigDecimal(String.valueOf(body.get("price")).trim()).setScale(2, RoundingMode.HALF_UP);
                if (newPrice.compareTo(BigDecimal.ZERO) < 0) {
                    return ResponseEntity.badRequest().body("Price cannot be negative.");
                }
                item.setPrice(newPrice);
            } catch (Exception ex) {
                return ResponseEntity.badRequest().body("Invalid price format. Use a valid number.");
            }
        }

        item.setUpdatedAt(Instant.now());
        MenuItem saved = menuItemRepository.save(item);

        if (oldPrice.compareTo(newPrice) != 0) {
            MenuPriceHistory hist = new MenuPriceHistory();
            hist.setMenuItemId(saved.getId());
            hist.setDishName(saved.getName());
            hist.setOldPrice(oldPrice);
            hist.setNewPrice(newPrice);
            hist.setChangedBy(StaffController.getSessionUsername(token));
            hist.setChangedAt(Instant.now());
            priceHistoryRepository.save(hist);
        }

        return ResponseEntity.ok(saved);
    }

    @PostMapping
    public ResponseEntity<?> createMenuItem(
            @RequestHeader(value = "X-Staff-Token", required = false) String token,
            @RequestBody MenuItem newItem) {

        if (!StaffController.isAdminToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Only ADMIN can add menu items.");
        }
        if (newItem.getName() == null || newItem.getName().isBlank()
                || newItem.getCategory() == null || newItem.getCategory().isBlank()
                || newItem.getPrice() == null || newItem.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body("Category, name, and valid non-negative price are required.");
        }
        newItem.setPrice(newItem.getPrice().setScale(2, RoundingMode.HALF_UP));
        newItem.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(menuItemRepository.save(newItem));
    }
}
