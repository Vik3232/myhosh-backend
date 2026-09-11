package com.myhosh.booking;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/tables")
public class TableController {

    // Bring in the repository so we can talk to the database
    private final RestaurantTableRepository repository;

    public TableController(RestaurantTableRepository repository) {
        this.repository = repository;
    }

    // Listens for a standard web request (GET) to http://localhost:8081/api/tables
    @GetMapping
    public List<RestaurantTable> getAllTables() {
        // This single line fetches all rows from PostgreSQL and turns them into a JSON list!
        return repository.findAll();
    }
}
