package com.example.catalogservice.repositories;

import com.example.catalogservice.entities.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCodeAllIgnoreCase(String code);
}
