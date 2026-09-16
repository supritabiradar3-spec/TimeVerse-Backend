package com.timeverse.backend.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.timeverse.backend.entity.Product;
import com.timeverse.backend.dto.ProductDto;
import com.timeverse.backend.repository.ProductRepository;
import com.timeverse.backend.service.ProductService;
import com.timeverse.backend.specification.ProductSpecification;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    // Get all products
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productRepository.findAll();
        List<Product> customerProducts = products.stream()
                .filter(p -> p.getName() == null || !p.getName().toLowerCase().contains("integration test"))
                .filter(p -> p.getDescription() == null || !p.getDescription().toLowerCase().contains("integration testing"))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(customerProducts);
    }

    // Get product by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null || 
            (product.getName() != null && product.getName().toLowerCase().contains("integration test")) ||
            (product.getDescription() != null && product.getDescription().toLowerCase().contains("integration testing"))) {
            return ResponseEntity.badRequest()
                    .body("Product not found with id: " + id);
        }
        return ResponseEntity.ok(product);
    }

    // Search + Filter + Pagination + Sorting
    @GetMapping("/filter")
    public ResponseEntity<Page<Product>> filterProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String subcategory,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "productId") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        // Build filter specification
        Specification<Product> specification =
                ProductSpecification.filterProducts(
                        keyword,
                        categoryId,
                        subcategory,
                        minPrice,
                        maxPrice,
                        inStock
                );

        // Sorting
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        // Pagination
        Pageable pageable = PageRequest.of(page, size, sort);

        // Fetch filtered, sorted and paginated data
        Page<Product> products =
                productRepository.findAll(specification, pageable);

        return ResponseEntity.ok(products);
    }

    // Create Product
    @PostMapping
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody ProductDto productDto) {
        ProductDto created = productService.createProduct(productDto);
        return ResponseEntity.ok(created);
    }

    // Update Product
    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductDto productDto) {
        ProductDto updated = productService.updateProduct(id, productDto);
        return ResponseEntity.ok(updated);
    }

    // Delete Product
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}