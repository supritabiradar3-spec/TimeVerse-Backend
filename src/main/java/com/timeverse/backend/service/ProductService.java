package com.timeverse.backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.timeverse.backend.dto.ProductDto;
import com.timeverse.backend.entity.Product;
import com.timeverse.backend.entity.ProductImage;
import com.timeverse.backend.exception.BadRequestException;
import com.timeverse.backend.exception.ResourceNotFoundException;
import com.timeverse.backend.repository.CategoryRepository;
import com.timeverse.backend.repository.ProductRepository;
import com.timeverse.backend.specification.ProductSpecification;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;


    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository) {

        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }


    // Get All Products
    public List<ProductDto> getAllProducts() {

        return productRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }


    // Search + Filter Products
    public List<ProductDto> filterProducts(
            String keyword,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock
    ) {

        Specification<Product> specification =
                ProductSpecification.filterProducts(
                        keyword,
                        categoryId,
                        minPrice,
                        maxPrice,
                        inStock
                );


        return productRepository.findAll(specification)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }



    // Get Product By Id
    public ProductDto getProductById(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + id));


        return convertToDto(product);
    }



    // Create Product
    public ProductDto createProduct(ProductDto dto) {


        if (!categoryRepository.existsById(dto.getCategoryId())) {

            throw new BadRequestException(
                    "Category not found with id: " + dto.getCategoryId());
        }


        Product product = Product.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .stock(dto.getStock())
                .categoryId(dto.getCategoryId())
                .subcategory(dto.getSubcategory())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .images(new ArrayList<>())
                .build();



        if(dto.getImageUrls()!=null){

            for(String url : dto.getImageUrls()){

                ProductImage image = ProductImage.builder()
                        .product(product)
                        .imageUrl(url)
                        .build();

                product.getImages().add(image);
            }
        }


        Product savedProduct = productRepository.save(product);


        return convertToDto(savedProduct);
    }



    // Update Product
    public ProductDto updateProduct(Long id, ProductDto dto) {


        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found with id: " + id));



        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setCategoryId(dto.getCategoryId());
        product.setSubcategory(dto.getSubcategory());
        product.setUpdatedAt(LocalDateTime.now());



        product.getImages().clear();



        if(dto.getImageUrls()!=null){

            for(String url : dto.getImageUrls()){

                ProductImage image = ProductImage.builder()
                        .product(product)
                        .imageUrl(url)
                        .build();


                product.getImages().add(image);
            }
        }



        Product updatedProduct = productRepository.save(product);


        return convertToDto(updatedProduct);
    }




    // Delete Product
    public void deleteProduct(Long id){

        if(!productRepository.existsById(id)){

            throw new ResourceNotFoundException(
                    "Product not found with id: " + id);
        }


        productRepository.deleteById(id);
    }




    // Convert Entity to DTO
    private ProductDto convertToDto(Product product){


        List<String> imageUrls =
                product.getImages()
                .stream()
                .map(ProductImage::getImageUrl)
                .collect(Collectors.toList());


        return ProductDto.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .categoryId(product.getCategoryId())
                .subcategory(product.getSubcategory())
                .imageUrls(imageUrls)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

}