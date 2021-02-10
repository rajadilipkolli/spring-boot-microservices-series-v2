package com.example.catalogservice.services;

import com.mycompany.myservice.entities.Customer;
import com.mycompany.myservice.repositories.CustomerRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogService {

    private final CatalogRepository catalogRepository;

    @Autowired
    public CatalogService(CatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    public List<Catalog> findAllCatalogs() {
        return catalogRepository.findAll();
    }

    public Optional<Catalog> findCatalogById(Long id) {
        return catalogRepository.findById(id);
    }

    public Catalog saveCatalog(Catalog catalog) {
        return catalogRepository.save(catalog);
    }

    public void deleteCatalogById(Long id) {
        catalogRepository.deleteById(id);
    }
}
