package com.myhosh.booking;

import jakarta.persistence.*;

@Entity // Tells Spring Boot: "This class represents a database table"
@Table(name = "restaurant_tables") // Must match the exact table name in PostgreSQL
public class RestaurantTable {

    @Id // Tells Java this is the Primary Key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Tells Java the DB auto-increments this (SERIAL)
    private Long id;

    // Notice how camelCase in Java (tableNumber) automatically maps to snake_case in SQL (table_number)
    private Integer tableNumber;
    private Integer capacity;
    private String location;

    // --- GETTERS AND SETTERS ---
    // Spring Boot needs these to read and write the data behind the scenes.

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getTableNumber() { return tableNumber; }
    public void setTableNumber(Integer tableNumber) { this.tableNumber = tableNumber; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}