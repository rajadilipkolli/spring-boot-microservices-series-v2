package com.example.catalogservice.web.controllers;

import com.mycompany.myservice.entities.Customer;
import com.mycompany.myservice.services.CustomerService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
@Slf4j
public class CatalogController {

    private final CatalogService catalogService;

    @Autowired
    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<Catalog> getAllCatalogs() {
        return catalogService.findAllCatalogs();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Catalog> getCatalogById(@PathVariable Long id) {
        return catalogService
                .findCatalogById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Catalog createCatalog(@RequestBody @Validated Catalog catalog) {
        return catalogService.saveCatalog(catalog);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Catalog> updateCatalog(
            @PathVariable Long id, @RequestBody Catalog catalog) {
        return catalogService
                .findCatalogById(id)
                .map(
                        catalogObj -> {
                            catalog.setId(id);
                            return ResponseEntity.ok(catalogService.saveCatalog(catalog));
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Catalog> deleteCatalog(@PathVariable Long id) {
        return catalogService
                .findCatalogById(id)
                .map(
                        catalog -> {
                            catalogService.deleteCatalogById(id);
                            return ResponseEntity.ok(catalog);
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
