package com.myhosh.booking;

import org.springframework.data.jpa.repository.JpaRepository;

// Notice it says "interface" and "extends JpaRepository"
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
