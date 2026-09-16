package com.timeverse.backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.timeverse.backend.entity.Product;
import com.timeverse.backend.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogRepairService {

    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    @Transactional(rollbackFor = Exception.class)
    public void repairLegacyCatalogProductAssignments(Long womenCatId, Long menCatId) {
        repairLegacyCatalogProductAssignmentsInternal(womenCatId, menCatId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void repairLegacyCatalogProductAssignmentsWithFailureHook(Long womenCatId, Long menCatId, Runnable postInsertHook) {
        repairLegacyCatalogProductAssignmentsInternal(womenCatId, menCatId, postInsertHook);
    }

    private void repairLegacyCatalogProductAssignmentsInternal(Long womenCatId, Long menCatId, Runnable postInsertHook) {
        if (womenCatId == null || womenCatId <= 0 || menCatId == null || menCatId <= 0) {
            log.warn("Catalog repair skipped: invalid category IDs (women: {}, men: {})", womenCatId, menCatId);
            return;
        }

        Optional<Product> p3Opt = productRepository.findById(3L);
        Optional<Product> p6Opt = productRepository.findById(6L);
        Optional<Product> p91Opt = productRepository.findById(91L);
        Optional<Product> p92Opt = productRepository.findById(92L);

        String name3 = p3Opt.map(p -> normalizeName(p.getName())).orElse("");
        String name6 = p6Opt.map(p -> normalizeName(p.getName())).orElse("");
        String name91 = p91Opt.map(p -> normalizeName(p.getName())).orElse("");
        String name92 = p92Opt.map(p -> normalizeName(p.getName())).orElse("");

        // =========================================================================
        // 1. IDEMPOTENCY CHECK
        // =========================================================================
        boolean alreadyComplete = name3.equals("sonata women's analog watch")
                && name6.equals("titan raga viva rose gold analog watch")
                && name91.equals("gosasa luxury hollowed men's watch")
                && name92.equals("mathey-tissot mathyiii");

        if (alreadyComplete) {
            log.info("Catalog repair already complete (IDs 3, 6, 91, 92 match canonical state). Skipping.");
            return;
        }

        // =========================================================================
        // 2. STRICT PRECONDITIONS
        // =========================================================================
        if (p3Opt.isEmpty() || !name3.equals("gosasa luxury hollowed men's watch")) {
            log.warn("Catalog repair skipped: product ID 3 precondition not met (expected 'Gosasa Luxury Hollowed Men\\'s Watch', found: '{}')",
                    p3Opt.map(Product::getName).orElse("null"));
            return;
        }

        if (p6Opt.isEmpty() || !name6.equals("mathey-tissot mathyiii")) {
            log.warn("Catalog repair skipped: product ID 6 precondition not met (expected 'Mathey-Tissot MathyIII', found: '{}')",
                    p6Opt.map(Product::getName).orElse("null"));
            return;
        }

        if (p91Opt.isPresent()) {
            log.warn("Catalog repair skipped: product ID 91 already exists with name '{}'", p91Opt.get().getName());
            return;
        }

        if (p92Opt.isPresent()) {
            log.warn("Catalog repair skipped: product ID 92 already exists with name '{}'", p92Opt.get().getName());
            return;
        }

        // =========================================================================
        // 3. UNIQUE CONSTRAINT & COLLISION SAFETY
        // =========================================================================
        long countOrderItems9192 = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM order_items WHERE product_id IN (91, 92)").getSingleResult()).longValue();
        long countCart9192 = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM cart WHERE product_id IN (91, 92)").getSingleResult()).longValue();
        long countWishlist9192 = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM wishlist WHERE product_id IN (91, 92)").getSingleResult()).longValue();
        long countReviews9192 = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM reviews WHERE product_id IN (91, 92)").getSingleResult()).longValue();

        if (countOrderItems9192 > 0 || countCart9192 > 0 || countWishlist9192 > 0 || countReviews9192 > 0) {
            throw new IllegalStateException(String.format(
                    "Pre-migration collision detected: Target product IDs 91/92 already referenced in order_items(%d), cart(%d), wishlist(%d), reviews(%d). Aborting.",
                    countOrderItems9192, countCart9192, countWishlist9192, countReviews9192));
        }

        log.info("Starting safe transactional production catalog repair for IDs 3, 6, 91, 92...");

        // =========================================================================
        // 4. STEP A & B: INSERT CANONICAL PRODUCTS 91 & 92
        // =========================================================================
        entityManager.createNativeQuery(
                "INSERT INTO products (product_id, name, description, price, stock, category_id, subcategory, created_at, updated_at) " +
                "VALUES (91, :name, :description, :price, :stock, :catId, :subcat, :createdAt, :updatedAt)")
                .setParameter("name", "Gosasa Luxury Hollowed Men's Watch")
                .setParameter("description", "Fashion skeleton mechanical watch")
                .setParameter("price", new BigDecimal("7999.00"))
                .setParameter("stock", 38)
                .setParameter("catId", menCatId)
                .setParameter("subcat", "Luxury")
                .setParameter("createdAt", LocalDateTime.now())
                .setParameter("updatedAt", LocalDateTime.now())
                .executeUpdate();
        log.info("Inserted canonical product ID 91 for Gosasa Luxury Hollowed Men's Watch");

        entityManager.createNativeQuery(
                "INSERT INTO products (product_id, name, description, price, stock, category_id, subcategory, created_at, updated_at) " +
                "VALUES (92, :name, :description, :price, :stock, :catId, :subcat, :createdAt, :updatedAt)")
                .setParameter("name", "Mathey-Tissot MathyIII")
                .setParameter("description", "Swiss made precision watch")
                .setParameter("price", new BigDecimal("15999.00"))
                .setParameter("stock", 49)
                .setParameter("catId", menCatId)
                .setParameter("subcat", "Luxury")
                .setParameter("createdAt", LocalDateTime.now())
                .setParameter("updatedAt", LocalDateTime.now())
                .executeUpdate();
        log.info("Inserted canonical product ID 92 for Mathey-Tissot MathyIII");

        // Failure injection point for rollback testing
        if (postInsertHook != null) {
            postInsertHook.run();
        }

        // =========================================================================
        // 5. STEP C: MOVE FK REFERENCES (3 -> 91, 6 -> 92)
        // =========================================================================
        int movedOrderItems3 = entityManager.createNativeQuery("UPDATE order_items SET product_id = 91 WHERE product_id = 3").executeUpdate();
        int movedOrderItems6 = entityManager.createNativeQuery("UPDATE order_items SET product_id = 92 WHERE product_id = 6").executeUpdate();
        log.info("FK order_items moved: 3->91 ({} rows), 6->92 ({} rows)", movedOrderItems3, movedOrderItems6);

        int movedCart3 = entityManager.createNativeQuery("UPDATE cart SET product_id = 91 WHERE product_id = 3").executeUpdate();
        int movedCart6 = entityManager.createNativeQuery("UPDATE cart SET product_id = 92 WHERE product_id = 6").executeUpdate();
        log.info("FK cart moved: 3->91 ({} rows), 6->92 ({} rows)", movedCart3, movedCart6);

        int movedWishlist3 = entityManager.createNativeQuery("UPDATE wishlist SET product_id = 91 WHERE product_id = 3").executeUpdate();
        int movedWishlist6 = entityManager.createNativeQuery("UPDATE wishlist SET product_id = 92 WHERE product_id = 6").executeUpdate();
        log.info("FK wishlist moved: 3->91 ({} rows), 6->92 ({} rows)", movedWishlist3, movedWishlist6);

        int movedReviews3 = entityManager.createNativeQuery("UPDATE reviews SET product_id = 91 WHERE product_id = 3").executeUpdate();
        int movedReviews6 = entityManager.createNativeQuery("UPDATE reviews SET product_id = 92 WHERE product_id = 6").executeUpdate();
        log.info("FK reviews moved: 3->91 ({} rows), 6->92 ({} rows)", movedReviews3, movedReviews6);

        // =========================================================================
        // 6. STEP E & H: ASSIGN / PRESERVE PRODUCT IMAGES
        // =========================================================================
        String gosasaImgUrl = "https://ik.imagekit.io/cyberstack/cyberstack/A3.jpg?updatedAt=1785159536501";
        String matheyImgUrl = "https://ik.imagekit.io/cyberstack/cyberstack/A6.jpg?updatedAt=1785159536610";
        String sonataImgUrl = "https://www.sonatawatches.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dwb52746f8/images/Sonata/Catalog/SP80181YM01W_1.jpg";
        String titanRagaImgUrl = "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw480b16bf/images/Titan/Catalog/2606WM01_1.jpg?sw=600&sh=600";

        entityManager.createNativeQuery("INSERT INTO productimages (product_id, image_url) VALUES (91, :imgUrl)")
                .setParameter("imgUrl", gosasaImgUrl)
                .executeUpdate();
        log.info("Inserted productimage for ID 91 (Gosasa)");

        entityManager.createNativeQuery("INSERT INTO productimages (product_id, image_url) VALUES (92, :imgUrl)")
                .setParameter("imgUrl", matheyImgUrl)
                .executeUpdate();
        log.info("Inserted productimage for ID 92 (Mathey-Tissot)");

        int updatedImg3 = entityManager.createNativeQuery("UPDATE productimages SET image_url = :imgUrl WHERE product_id = 3")
                .setParameter("imgUrl", sonataImgUrl)
                .executeUpdate();
        if (updatedImg3 == 0) {
            entityManager.createNativeQuery("INSERT INTO productimages (product_id, image_url) VALUES (3, :imgUrl)")
                    .setParameter("imgUrl", sonataImgUrl)
                    .executeUpdate();
        }
        log.info("Updated productimage for ID 3 to Sonata image");

        int updatedImg6 = entityManager.createNativeQuery("UPDATE productimages SET image_url = :imgUrl WHERE product_id = 6")
                .setParameter("imgUrl", titanRagaImgUrl)
                .executeUpdate();
        if (updatedImg6 == 0) {
            entityManager.createNativeQuery("INSERT INTO productimages (product_id, image_url) VALUES (6, :imgUrl)")
                    .setParameter("imgUrl", titanRagaImgUrl)
                    .executeUpdate();
        }
        log.info("Updated productimage for ID 6 to Titan Raga image");

        // =========================================================================
        // 7. STEP F & G: CONVERT PRODUCT 3 AND PRODUCT 6 METADATA
        // =========================================================================
        entityManager.createNativeQuery(
                "UPDATE products SET name = :name, description = :description, price = :price, stock = :stock, " +
                "category_id = :catId, subcategory = :subcat, updated_at = :updatedAt WHERE product_id = 3")
                .setParameter("name", "Sonata Women's Analog Watch")
                .setParameter("description", "Stylish quartz analog watch with a clean dial and comfortable strap for daily use.")
                .setParameter("price", new BigDecimal("1599.00"))
                .setParameter("stock", 57)
                .setParameter("catId", womenCatId)
                .setParameter("subcat", "Analog")
                .setParameter("updatedAt", LocalDateTime.now())
                .executeUpdate();
        log.info("Converted product ID 3 to Sonata Women's Analog Watch");

        entityManager.createNativeQuery(
                "UPDATE products SET name = :name, description = :description, price = :price, stock = :stock, " +
                "category_id = :catId, subcategory = :subcat, updated_at = :updatedAt WHERE product_id = 6")
                .setParameter("name", "Titan Raga Viva Rose Gold Analog Watch")
                .setParameter("description", "Exquisite women's analog watch from the Raga Viva collection featuring a sunray rose gold dial, jewel-cut mineral glass, and an intricately designed metal bracelet.")
                .setParameter("price", new BigDecimal("5995.00"))
                .setParameter("stock", 18)
                .setParameter("catId", womenCatId)
                .setParameter("subcat", "Analog")
                .setParameter("updatedAt", LocalDateTime.now())
                .executeUpdate();
        log.info("Converted product ID 6 to Titan Raga Viva Rose Gold Analog Watch");

        entityManager.flush();
        entityManager.clear();
        log.info("Safe production catalog repair for IDs 3, 6, 91, 92 completed successfully.");
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase();
    }
}
