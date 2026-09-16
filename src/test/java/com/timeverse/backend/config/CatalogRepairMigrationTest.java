package com.timeverse.backend.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.timeverse.backend.entity.Category;
import com.timeverse.backend.entity.Order;
import com.timeverse.backend.entity.Product;
import com.timeverse.backend.entity.User;
import com.timeverse.backend.repository.CategoryRepository;
import com.timeverse.backend.repository.OrderRepository;
import com.timeverse.backend.repository.ProductRepository;
import com.timeverse.backend.repository.UserRepository;
import com.timeverse.backend.service.CatalogRepairService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.mail.username=test-mail@gmail.com",
    "spring.mail.password=test-password"
})
public class CatalogRepairMigrationTest {

    @Autowired
    private CatalogRepairService catalogRepairService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User getOrCreateTestUser() {
        return userRepository.findByEmail("testmigration@timeverse.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("testmigration@timeverse.com")
                        .username("testmigration_" + System.currentTimeMillis())
                        .fullName("Test Migration User")
                        .password("Password@123")
                        .role("CUSTOMER")
                        .emailVerified(true)
                        .firstLoginOtpVerified(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    private void setupStaleCatalogState(Long menCatId, User testUser, Order testOrder) {
        // 1. Clean up potential leftover rows for 91 and 92
        entityManager.createNativeQuery("DELETE FROM productimages WHERE product_id IN (91, 92)").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM order_items WHERE product_id IN (91, 92)").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM cart WHERE product_id IN (91, 92)").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM wishlist WHERE product_id IN (91, 92)").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM reviews WHERE product_id IN (91, 92)").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM products WHERE product_id IN (91, 92)").executeUpdate();

        // 2. Set up stale state on Product 3 & 6 via UPDATE
        entityManager.createNativeQuery(
                "UPDATE products SET name = :name, description = :description, " +
                "price = :price, stock = :stock, category_id = :catId, subcategory = :subcat, updated_at = :now WHERE product_id = 3")
                .setParameter("name", "Gosasa Luxury Hollowed Men's Watch")
                .setParameter("description", "Fashion skeleton mechanical watch")
                .setParameter("price", new BigDecimal("7999.00"))
                .setParameter("stock", 38)
                .setParameter("catId", menCatId)
                .setParameter("subcat", "Luxury")
                .setParameter("now", LocalDateTime.now())
                .executeUpdate();

        entityManager.createNativeQuery(
                "UPDATE products SET name = :name, description = :description, " +
                "price = :price, stock = :stock, category_id = :catId, subcategory = :subcat, updated_at = :now WHERE product_id = 6")
                .setParameter("name", "Mathey-Tissot MathyIII")
                .setParameter("description", "Swiss made precision watch")
                .setParameter("price", new BigDecimal("15999.00"))
                .setParameter("stock", 49)
                .setParameter("catId", menCatId)
                .setParameter("subcat", "Luxury")
                .setParameter("now", LocalDateTime.now())
                .executeUpdate();

        entityManager.createNativeQuery("UPDATE productimages SET image_url = 'https://ik.imagekit.io/cyberstack/cyberstack/A3.jpg' WHERE product_id = 3").executeUpdate();
        entityManager.createNativeQuery("UPDATE productimages SET image_url = 'https://ik.imagekit.io/cyberstack/cyberstack/A6.jpg' WHERE product_id = 6").executeUpdate();

        // 3. Clean and add test FK rows referencing products 3 & 6
        entityManager.createNativeQuery("DELETE FROM order_items WHERE order_id = :orderId").setParameter("orderId", testOrder.getOrderId()).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM cart WHERE user_id = :userId").setParameter("userId", testUser.getUserId()).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM wishlist WHERE user_id = :userId").setParameter("userId", testUser.getUserId()).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM reviews WHERE user_id = :userId").setParameter("userId", testUser.getUserId()).executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO order_items (order_id, product_id, quantity, price) VALUES (:orderId, 3, 1, 7999.00)")
                .setParameter("orderId", testOrder.getOrderId()).executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO order_items (order_id, product_id, quantity, price) VALUES (:orderId, 6, 2, 15999.00)")
                .setParameter("orderId", testOrder.getOrderId()).executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO cart (user_id, product_id, quantity, created_at) VALUES (:userId, 3, 1, :now)")
                .setParameter("userId", testUser.getUserId())
                .setParameter("now", LocalDateTime.now()).executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO cart (user_id, product_id, quantity, created_at) VALUES (:userId, 6, 1, :now)")
                .setParameter("userId", testUser.getUserId())
                .setParameter("now", LocalDateTime.now()).executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO wishlist (user_id, product_id, created_at) VALUES (:userId, 3, :now)")
                .setParameter("userId", testUser.getUserId())
                .setParameter("now", LocalDateTime.now()).executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO wishlist (user_id, product_id, created_at) VALUES (:userId, 6, :now)")
                .setParameter("userId", testUser.getUserId())
                .setParameter("now", LocalDateTime.now()).executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO reviews (product_id, user_id, username, rating, comment, created_at) " +
                "VALUES (3, :userId, :username, 5, 'Great Gosasa watch', :now)")
                .setParameter("userId", testUser.getUserId())
                .setParameter("username", testUser.getUsername())
                .setParameter("now", LocalDateTime.now()).executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO reviews (product_id, user_id, username, rating, comment, created_at) " +
                "VALUES (6, :userId, :username, 5, 'Great Mathey watch', :now)")
                .setParameter("userId", testUser.getUserId())
                .setParameter("username", testUser.getUsername())
                .setParameter("now", LocalDateTime.now()).executeUpdate();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Verify safe transactional catalog repair across Spring proxy, FK remapping, and idempotency")
    void testCatalogRepairMigrationEndToEnd() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Category womenCat = categoryRepository.findByCategoryName("Women")
                .orElseGet(() -> categoryRepository.save(Category.builder().categoryName("Women").build()));
        Category menCat = categoryRepository.findByCategoryName("Men")
                .orElseGet(() -> categoryRepository.save(Category.builder().categoryName("Men").build()));

        Long womenCatId = womenCat.getCategoryId();
        Long menCatId = menCat.getCategoryId();

        User testUser = getOrCreateTestUser();
        Order testOrder = orderRepository.save(Order.builder()
                .userId(testUser.getUserId())
                .totalAmount(new BigDecimal("23998.00"))
                .status("DELIVERED")
                .createdAt(LocalDateTime.now())
                .build());

        tx.execute(status -> {
            setupStaleCatalogState(menCatId, testUser, testOrder);
            return null;
        });

        // =========================================================================
        // 1. RUN REPAIR MIGRATION VIA SPRING PROXY
        // =========================================================================
        catalogRepairService.repairLegacyCatalogProductAssignments(womenCatId, menCatId);

        // =========================================================================
        // 2. VERIFY POST-MIGRATION RESULTS
        // =========================================================================
        tx.execute(status -> {
            Product p3 = productRepository.findById(3L).orElseThrow();
            assertEquals("Sonata Women's Analog Watch", p3.getName());
            assertEquals("Analog", p3.getSubcategory());
            assertEquals(womenCatId, p3.getCategoryId());
            assertEquals(new BigDecimal("1599.00"), p3.getPrice());
            assertEquals(57, p3.getStock());

            Product p6 = productRepository.findById(6L).orElseThrow();
            assertEquals("Titan Raga Viva Rose Gold Analog Watch", p6.getName());
            assertEquals("Analog", p6.getSubcategory());
            assertEquals(womenCatId, p6.getCategoryId());
            assertEquals(new BigDecimal("5995.00"), p6.getPrice());
            assertEquals(18, p6.getStock());

            Product p91 = productRepository.findById(91L).orElseThrow();
            assertEquals("Gosasa Luxury Hollowed Men's Watch", p91.getName());
            assertEquals("Luxury", p91.getSubcategory());
            assertEquals(menCatId, p91.getCategoryId());
            assertEquals(new BigDecimal("7999.00"), p91.getPrice());
            assertEquals(38, p91.getStock());

            Product p92 = productRepository.findById(92L).orElseThrow();
            assertEquals("Mathey-Tissot MathyIII", p92.getName());
            assertEquals("Luxury", p92.getSubcategory());
            assertEquals(menCatId, p92.getCategoryId());
            assertEquals(new BigDecimal("15999.00"), p92.getPrice());
            assertEquals(49, p92.getStock());

            // Verify FK remapping: 3 -> 91, 6 -> 92
            long countOrderItems91 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM order_items WHERE order_id = :orderId AND product_id = 91")
                    .setParameter("orderId", testOrder.getOrderId()).getSingleResult()).longValue();
            long countOrderItems92 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM order_items WHERE order_id = :orderId AND product_id = 92")
                    .setParameter("orderId", testOrder.getOrderId()).getSingleResult()).longValue();
            assertEquals(1, countOrderItems91, "order_item should now point to 91");
            assertEquals(1, countOrderItems92, "order_item should now point to 92");

            long countCart91 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM cart WHERE user_id = :userId AND product_id = 91")
                    .setParameter("userId", testUser.getUserId()).getSingleResult()).longValue();
            long countCart92 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM cart WHERE user_id = :userId AND product_id = 92")
                    .setParameter("userId", testUser.getUserId()).getSingleResult()).longValue();
            assertEquals(1, countCart91);
            assertEquals(1, countCart92);

            long countWishlist91 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM wishlist WHERE user_id = :userId AND product_id = 91")
                    .setParameter("userId", testUser.getUserId()).getSingleResult()).longValue();
            long countWishlist92 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM wishlist WHERE user_id = :userId AND product_id = 92")
                    .setParameter("userId", testUser.getUserId()).getSingleResult()).longValue();
            assertEquals(1, countWishlist91);
            assertEquals(1, countWishlist92);

            long countReviews91 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM reviews WHERE user_id = :userId AND product_id = 91")
                    .setParameter("userId", testUser.getUserId()).getSingleResult()).longValue();
            long countReviews92 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM reviews WHERE user_id = :userId AND product_id = 92")
                    .setParameter("userId", testUser.getUserId()).getSingleResult()).longValue();
            assertEquals(1, countReviews91);
            assertEquals(1, countReviews92);

            // Verify images
            @SuppressWarnings("unchecked")
            List<String> img3 = entityManager.createNativeQuery("SELECT image_url FROM productimages WHERE product_id = 3").getResultList();
            assertTrue(img3.get(0).contains("Sonata") || img3.get(0).contains("SP80181YM01W"));

            @SuppressWarnings("unchecked")
            List<String> img6 = entityManager.createNativeQuery("SELECT image_url FROM productimages WHERE product_id = 6").getResultList();
            assertTrue(img6.get(0).contains("2606WM01_1.jpg"));

            @SuppressWarnings("unchecked")
            List<String> img91 = entityManager.createNativeQuery("SELECT image_url FROM productimages WHERE product_id = 91").getResultList();
            assertTrue(img91.get(0).contains("A3.jpg"));

            @SuppressWarnings("unchecked")
            List<String> img92 = entityManager.createNativeQuery("SELECT image_url FROM productimages WHERE product_id = 92").getResultList();
            assertTrue(img92.get(0).contains("A6.jpg"));

            return null;
        });

        // =========================================================================
        // 3. IDEMPOTENCY TEST: SECOND EXECUTION MUST BE A NO-OP
        // =========================================================================
        assertDoesNotThrow(() -> catalogRepairService.repairLegacyCatalogProductAssignments(womenCatId, menCatId));

        Product p3AfterSecond = productRepository.findById(3L).orElseThrow();
        assertEquals("Sonata Women's Analog Watch", p3AfterSecond.getName());
        Product p91AfterSecond = productRepository.findById(91L).orElseThrow();
        assertEquals("Gosasa Luxury Hollowed Men's Watch", p91AfterSecond.getName());
    }

    @Test
    @DisplayName("Verify atomic transaction rollback on failure during catalog repair")
    void testCatalogRepairTransactionRollbackOnFailure() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Category womenCat = categoryRepository.findByCategoryName("Women")
                .orElseGet(() -> categoryRepository.save(Category.builder().categoryName("Women").build()));
        Category menCat = categoryRepository.findByCategoryName("Men")
                .orElseGet(() -> categoryRepository.save(Category.builder().categoryName("Men").build()));

        Long womenCatId = womenCat.getCategoryId();
        Long menCatId = menCat.getCategoryId();

        User testUser = getOrCreateTestUser();
        Order testOrder = orderRepository.save(Order.builder()
                .userId(testUser.getUserId())
                .totalAmount(new BigDecimal("23998.00"))
                .status("DELIVERED")
                .createdAt(LocalDateTime.now())
                .build());

        tx.execute(status -> {
            setupStaleCatalogState(menCatId, testUser, testOrder);
            return null;
        });

        // Attempt repair with simulated mid-migration failure after 91/92 insert
        assertThrows(RuntimeException.class, () ->
                catalogRepairService.repairLegacyCatalogProductAssignmentsWithFailureHook(
                        womenCatId, menCatId, () -> {
                            throw new RuntimeException("Simulated mid-migration failure for rollback test");
                        }
                )
        );

        // Verify that ALL changes were rolled back atomically:
        tx.execute(status -> {
            // 1. IDs 91 and 92 must NOT exist
            assertTrue(productRepository.findById(91L).isEmpty(), "Product 91 must not exist after rollback");
            assertTrue(productRepository.findById(92L).isEmpty(), "Product 92 must not exist after rollback");

            // 2. Product 3 and 6 must remain unchanged (Gosasa and Mathey)
            Product p3 = productRepository.findById(3L).orElseThrow();
            assertEquals("Gosasa Luxury Hollowed Men's Watch", p3.getName(), "Product 3 must remain Gosasa after rollback");

            Product p6 = productRepository.findById(6L).orElseThrow();
            assertEquals("Mathey-Tissot MathyIII", p6.getName(), "Product 6 must remain Mathey-Tissot after rollback");

            // 3. FKs must STILL point to 3 and 6, NOT 91 or 92
            long countOrderItems3 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM order_items WHERE order_id = :orderId AND product_id = 3")
                    .setParameter("orderId", testOrder.getOrderId()).getSingleResult()).longValue();
            long countOrderItems6 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM order_items WHERE order_id = :orderId AND product_id = 6")
                    .setParameter("orderId", testOrder.getOrderId()).getSingleResult()).longValue();
            assertEquals(1, countOrderItems3, "order_item must still point to 3");
            assertEquals(1, countOrderItems6, "order_item must still point to 6");

            long countOrderItems91 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM order_items WHERE order_id = :orderId AND product_id = 91")
                    .setParameter("orderId", testOrder.getOrderId()).getSingleResult()).longValue();
            long countOrderItems92 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM order_items WHERE order_id = :orderId AND product_id = 92")
                    .setParameter("orderId", testOrder.getOrderId()).getSingleResult()).longValue();
            assertEquals(0, countOrderItems91, "order_item must not point to 91");
            assertEquals(0, countOrderItems92, "order_item must not point to 92");

            // 4. Cart / Wishlist / Reviews must still point to 3 and 6
            long countCart3 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM cart WHERE user_id = :userId AND product_id = 3")
                    .setParameter("userId", testUser.getUserId()).getSingleResult()).longValue();
            assertEquals(1, countCart3);

            long countCart91 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM cart WHERE user_id = :userId AND product_id = 91")
                    .setParameter("userId", testUser.getUserId()).getSingleResult()).longValue();
            assertEquals(0, countCart91);

            // 5. Product images for 91 and 92 must not exist
            long countImages91 = ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM productimages WHERE product_id = 91").getSingleResult()).longValue();
            assertEquals(0, countImages91, "Images for 91 must not exist after rollback");

            return null;
        });
    }
}
