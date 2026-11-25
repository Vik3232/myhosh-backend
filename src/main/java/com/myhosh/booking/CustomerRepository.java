package com.myhosh.booking;

import org.springframework.data.jpa.repository.JpaRepository;

// Notice it says "interface" and "extends JpaRepository"
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query(
            "DELETE FROM Customer c WHERE NOT EXISTS (SELECT 1 FROM Booking b WHERE b.customer = c)")
    int deleteOrphanedCustomers();
}
