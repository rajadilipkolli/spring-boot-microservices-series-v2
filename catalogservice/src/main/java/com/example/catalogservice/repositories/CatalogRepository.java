package com.example.catalogservice.repositories;

import com.example.catalogservice.entities.Catalog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogRepository extends JpaRepository<Catalog, Long> {}
