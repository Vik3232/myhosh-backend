package com.myhosh.booking;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerRepository repository;

    public CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    // 1. GET Request: Read all customers
    @GetMapping
    public List<Customer> getAllCustomers() {
        return repository.findAll();
    }

    // 2. POST Request: Create a new customer from the website!
    @PostMapping
    public Customer createCustomer(@RequestBody Customer newCustomer) {
        // The @RequestBody tag takes the JSON from the website and turns it into a Java Customer
        // The .save() command writes it directly into PostgreSQL
        return repository.save(newCustomer);
    }
}