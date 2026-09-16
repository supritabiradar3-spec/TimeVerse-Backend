package com.timeverse.backend.specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.timeverse.backend.entity.Product;

import jakarta.persistence.criteria.Predicate;

public class ProductSpecification {

    public static Specification<Product> filterProducts(
            String keyword,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock) {
        return filterProducts(keyword, categoryId, null, minPrice, maxPrice, inStock);
    }

    public static Specification<Product> filterProducts(
            String keyword,
            Long categoryId,
            String subcategory,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock) {

        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            // Exclude test products from queries
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("name")),
                    criteriaBuilder.notLike(criteriaBuilder.lower(root.get("name")), "%integration test%")
            ));
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("description")),
                    criteriaBuilder.notLike(criteriaBuilder.lower(root.get("description")), "%integration testing%")
            ));

            if (keyword != null && !keyword.isBlank()) {
                predicates.add(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("name")),
                                "%" + keyword.toLowerCase() + "%"));
            }

            if (categoryId != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("categoryId"),
                                categoryId));
            }

            if (subcategory != null && !subcategory.isBlank()) {
                predicates.add(
                        criteriaBuilder.equal(
                                criteriaBuilder.lower(root.get("subcategory")),
                                subcategory.trim().toLowerCase()));
            }

            if (minPrice != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("price"),
                                minPrice));
            }

            if (maxPrice != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("price"),
                                maxPrice));
            }

            if (Boolean.TRUE.equals(inStock)) {
                predicates.add(
                        criteriaBuilder.greaterThan(
                                root.get("stock"),
                                0));
            }

            return criteriaBuilder.and(
                    predicates.toArray(new Predicate[0]));
        };
    }
}