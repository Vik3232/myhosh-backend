package com.myhosh.booking;

import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository takes two things: <The Entity Name, The Data Type of the Primary Key>
// By extending this, you instantly get access to commands like .findAll(), .save(), and .delete()
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {
}