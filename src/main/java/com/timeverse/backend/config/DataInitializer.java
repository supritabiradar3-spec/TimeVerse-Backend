package com.timeverse.backend.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.timeverse.backend.entity.Category;
import com.timeverse.backend.entity.Product;
import com.timeverse.backend.entity.ProductImage;
import com.timeverse.backend.entity.User;
import com.timeverse.backend.repository.CategoryRepository;
import com.timeverse.backend.repository.ProductRepository;
import com.timeverse.backend.repository.UserRepository;
import com.timeverse.backend.service.CatalogRepairService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final CatalogRepairService catalogRepairService;

    @Value("${app.admin.initial-password:${ADMIN_INITIAL_PASSWORD:}}")
    private String adminInitialPassword;

    private static final String[] REQUIRED_CATEGORIES = {
            "Women",
            "Men",
            "Kids",
            "Couples"
    };

    @Override
    @Transactional
    public void run(String... args) {
        initializeAdminUser();
        initializeCategoriesAndProducts();
    }

    private void initializeAdminUser() {
        try {
            boolean hasAdmin = userRepository.existsByRoleIgnoreCase("ADMIN");
            if (!hasAdmin) {
                if (adminInitialPassword == null || adminInitialPassword.trim().isEmpty()) {
                    log.info("Administrator initialization skipped: ADMIN_INITIAL_PASSWORD is not configured.");
                    return;
                }
                String adminEmail = "admin@timeverse.com";
                Optional<User> existingUserOpt = userRepository.findByEmail(adminEmail);

                if (existingUserOpt.isPresent()) {
                    User user = existingUserOpt.get();
                    user.setRole("ADMIN");
                    user.setEmailVerified(true);
                    user.setFirstLoginOtpVerified(true);
                    user.setUpdatedAt(LocalDateTime.now());
                    userRepository.save(user);
                    log.info("Elevated existing user {} to ADMIN role.", adminEmail);
                } else {
                    String username = "admin";
                    if (userRepository.existsByUsername(username)) {
                        username = "admin_" + System.currentTimeMillis();
                    }

                    User admin = User.builder()
                            .fullName("TimeVerse Administrator")
                            .username(username)
                            .email(adminEmail)
                            .password(passwordEncoder.encode(adminInitialPassword.trim()))
                            .role("ADMIN")
                            .emailVerified(true)
                            .firstLoginOtpVerified(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    userRepository.save(admin);
                    log.info("Initialized default Administrator account ({}).", adminEmail);
                }
            }
        } catch (Exception ex) {
            log.warn("Administrator initialization notice: {}", ex.getMessage());
        }
    }

    private void initializeCategoriesAndProducts() {
        try {
            // =========================================================================
            // STEP 1: Create / verify the 4 main categories first (Women, Men, Kids, Couples)
            // =========================================================================
            Map<String, Long> categoryNameToId = new HashMap<>();

            for (String categoryName : REQUIRED_CATEGORIES) {
                Optional<Category> catOpt = categoryRepository.findByCategoryName(categoryName);
                Category cat;
                if (catOpt.isPresent()) {
                    cat = catOpt.get();
                } else {
                    cat = categoryRepository.save(Category.builder().categoryName(categoryName).build());
                    log.info("Step 1: Created category '{}' (ID: {})", categoryName, cat.getCategoryId());
                }
                categoryNameToId.put(categoryName, cat.getCategoryId());
            }

            Long womenCatId = categoryNameToId.get("Women");
            Long menCatId = categoryNameToId.get("Men");
            Long kidsCatId = categoryNameToId.get("Kids");
            Long couplesCatId = categoryNameToId.get("Couples");

            Map<String, Long> categoryMapping = Map.of(
                    "Women", womenCatId,
                    "Men", menCatId,
                    "Kids", kidsCatId,
                    "Couples", couplesCatId
            );

            log.info("Step 1 Complete: Verified 4 categories -> Women (ID: {}), Men (ID: {}), Kids (ID: {}), Couples (ID: {})",
                    womenCatId, menCatId, kidsCatId, couplesCatId);

            // =========================================================================
            // STEP 1.5: Safe One-Time Production Catalog Repair (IDs 3, 6, 91, 92)
            // =========================================================================
            catalogRepairService.repairLegacyCatalogProductAssignments(womenCatId, menCatId);

            // =========================================================================
            // STEP 2: Create / assign the 80 products with 2-level category hierarchy
            // (4 main categories x 4 subcategories = 16 buckets x 5 products = 80 total)
            // =========================================================================
            Map<Long, ProductSeedData> seeds = getCatalogSeeds();

            List<Product> existingProducts = productRepository.findAll();
            Map<Long, Product> existingById = new HashMap<>();
            Map<String, Product> existingByName = new HashMap<>();
            for (Product p : existingProducts) {
                if (p.getProductId() != null) {
                    existingById.put(p.getProductId(), p);
                }
                if (p.getName() != null) {
                    existingByName.put(p.getName().trim().toLowerCase(), p);
                }
            }

            Set<Long> claimedProductIds = new HashSet<>();
            int updatedCount = 0;
            int createdCount = 0;

            for (Map.Entry<Long, ProductSeedData> entry : seeds.entrySet()) {
                Long productId = entry.getKey();
                ProductSeedData seed = entry.getValue();
                Long targetCatId = categoryMapping.getOrDefault(seed.categoryName, menCatId);

                Product product = existingById.get(productId);
                if (product != null) {
                    claimedProductIds.add(product.getProductId());
                } else if (seed.name != null) {
                    // Only match by existing name if the candidate row is not already owned by or reserved for another target seed ID
                    Product candidate = existingByName.get(seed.name.trim().toLowerCase());
                    if (candidate != null && candidate.getProductId() != null
                            && !claimedProductIds.contains(candidate.getProductId())
                            && !seeds.containsKey(candidate.getProductId())) {
                        product = candidate;
                        claimedProductIds.add(product.getProductId());
                    }
                }

                if (product != null) {
                    // Update existing product without creating duplicates
                    product.setName(seed.name);
                    product.setDescription(seed.description);
                    product.setPrice(seed.price);
                    if (product.getStock() == null || product.getStock() <= 0) {
                        product.setStock(seed.stock);
                    }
                    product.setCategoryId(targetCatId);
                    product.setSubcategory(seed.subcategory);
                    product.setUpdatedAt(LocalDateTime.now());

                    // Ensure product image is set idempotently
                    if (product.getImages() == null || product.getImages().isEmpty()) {
                        ProductImage img = ProductImage.builder()
                                .product(product)
                                .imageUrl(seed.imageUrl)
                                .build();
                        if (product.getImages() == null) {
                            product.setImages(new ArrayList<>());
                        }
                        product.getImages().add(img);
                    } else if (seed.imageUrl != null && !seed.imageUrl.isBlank()) {
                        ProductImage firstImg = product.getImages().get(0);
                        firstImg.setImageUrl(seed.imageUrl);
                    }

                    productRepository.save(product);
                    updatedCount++;
                } else {
                    // Create new product under target category and subcategory
                    Product newProd = Product.builder()
                            .name(seed.name)
                            .description(seed.description)
                            .price(seed.price)
                            .stock(seed.stock)
                            .categoryId(targetCatId)
                            .subcategory(seed.subcategory)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .images(new ArrayList<>())
                            .build();

                    ProductImage img = ProductImage.builder()
                            .product(newProd)
                            .imageUrl(seed.imageUrl)
                            .build();
                    newProd.getImages().add(img);

                    productRepository.save(newProd);
                    createdCount++;
                }
            }

            log.info("Step 2 Complete: Initialized catalog products (Updated: {}, Created: {}) with 160 categorized products across 4 categories.",
                    updatedCount, createdCount);

        } catch (Exception ex) {
            log.error("Category and product initialization error: {}", ex.getMessage(), ex);
        }
    }

    private static class ProductSeedData {
        String name;
        String description;
        BigDecimal price;
        Integer stock;
        String categoryName;
        String subcategory;
        String imageUrl;

        ProductSeedData(String name, String description, double price, int stock, String categoryName, String subcategory, String imageUrl) {
            this.name = name;
            this.description = description;
            this.price = BigDecimal.valueOf(price);
            this.stock = stock;
            this.categoryName = categoryName;
            this.subcategory = subcategory;
            this.imageUrl = imageUrl;
        }
    }

    private Map<Long, ProductSeedData> getCatalogSeeds() {
        Map<Long, ProductSeedData> seeds = new LinkedHashMap<>();

        // 1. WOMEN COLLECTION -> Women - Analog (10)
        seeds.put(1L, new ProductSeedData("Titan Women's Analog Watch", "Elegant women's analog watch with a refined dial and premium finish for everyday and formal wear.", 8995.00, 11, "Women", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dwbcad5c4f/images/Titan/Catalog/95352WM01_1.jpg"));
        seeds.put(2L, new ProductSeedData("Casio Women's Analog Watch", "Classic women's analog timepiece with an easy-to-read dial and elegant styling.", 2495.00, 32, "Women", "Analog", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/L/LT/LTP/LTP-VT01GL-7B/assets/LTP-VT01GL-7B_Seq1.jpg.transform/main-visual-sp/image.jpg"));
        seeds.put(3L, new ProductSeedData("Sonata Women's Analog Watch", "Stylish quartz analog watch with a clean dial and comfortable strap for daily use.", 1599.00, 57, "Women", "Analog", "https://www.sonatawatches.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dwb52746f8/images/Sonata/Catalog/SP80181YM01W_1.jpg"));
        seeds.put(4L, new ProductSeedData("Anne Klein Women's Analog Watch", "Sophisticated analog watch featuring an elegant feminine design and premium finish.", 8499.00, 25, "Women", "Analog", "https://ik.imagekit.io/Cyberstack/Anne%20Klein%20New%20York%20Analogue%20Women's%20Watch%20(Rose%20Gold%20Dial%20Rose%20Gold%20Colored%20Strap).webp?updatedAt=1786171907269"));
        seeds.put(5L, new ProductSeedData("Tommy Hilfiger Women's Analog Watch", "Elegant women's analog watch with a refined dial, polished case, and sophisticated everyday styling.", 11995.00, 24, "Women", "Analog", "https://ik.imagekit.io/Cyberstack/Tommy%20Hilfiger%20Liberty%20Montre%20Femme%20Acier%20Bleu%20Milanais.jpg?updatedAt=1786171907724"));
        seeds.put(6L, new ProductSeedData("Titan Raga Viva Rose Gold Analog Watch", "Exquisite women's analog watch from the Raga Viva collection featuring a sunray rose gold dial, jewel-cut mineral glass, and an intricately designed metal bracelet.", 5995.00, 18, "Women", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw480b16bf/images/Titan/Catalog/2606WM01_1.jpg?sw=600&sh=600"));
        seeds.put(7L, new ProductSeedData("Casio Enticer Women LTP-V007D-7E", "Classic rectangular analog watch for women with a sleek stainless steel bracelet, clean Roman numeral indices, and accurate quartz timekeeping.", 2995.00, 22, "Women", "Analog", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/L/LT/LTP/LTP-V007D-7E/assets/LTP-V007D-7E_Seq1.jpg"));
        seeds.put(8L, new ProductSeedData("Fossil Carlie Mini Rose Gold Analog Watch", "Delicate 28mm women's analog watch featuring a shimmering mother-of-pearl dial, Roman numeral indices, and an elegant rose gold-tone mesh bracelet.", 9495.00, 15, "Women", "Analog", "https://fossil.scene7.com/is/image/FossilPartners/ES4433_main?$sfcc_fos_large$"));
        seeds.put(9L, new ProductSeedData("Timex Transcend Rose Gold Analog Watch", "Ultra-slim women's analog watch showcasing a rose gold round dial, minimalist hour markers, and a sophisticated stainless steel mesh bracelet.", 8995.00, 20, "Women", "Analog", "https://cdn.shopify.com/s/files/1/0787/5375/9521/files/TW2V52500UJ_1.jpg?v=1726562369"));
        seeds.put(10L, new ProductSeedData("Michael Kors Pyper Gold-Tone Analog Watch", "Luxurious 38mm women's analog watch featuring a polished gold-tone dial with crystal hour markers, stainless steel case, and matching three-link bracelet.", 13995.00, 16, "Women", "Analog", "https://fossil.scene7.com/is/image/FossilPartners/MK3898_main?$sfcc_fos_large$"));

        // Women - Digital (10)
        seeds.put(41L, new ProductSeedData("Casio Vintage LA670WGA-1DF Petite Gold Watch", "Petite gold digital watch for women featuring daily alarm, countdown timer, 1/10-second stopwatch, and gold ion-plated band.", 2495.00, 19, "Women", "Digital", "https://ik.imagekit.io/Cyberstack/Extra%20photo/Buy%20CASIO%20Vintage%20LA670WGA-1DF%20Black%20Digital%20Dial%20Gold%20Stainless%20Steel%20Band%20D124.jpg?updatedAt=1786173696852"));
        seeds.put(42L, new ProductSeedData("Casio Vintage B640WC-5ADF Rose Gold Watch", "Rose gold vintage digital watch with LED backlight, 1/100-second stopwatch, and 50m water resistance for women.", 3995.00, 25, "Women", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/B/B6/B64/B640WC-5A/assets/B640WC-5A.png.transform/main-visual-sp/image.png"));
        seeds.put(43L, new ProductSeedData("Casio Vintage LA670WA-1DF Petite Silver Watch", "Petite silver stainless steel digital watch for women featuring daily alarm, countdown timer, and 1/10-second stopwatch.", 1695.00, 28, "Women", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/L/LA/LA6/LA670WA-1/assets/LA670WA-1_Seq1.jpg"));
        seeds.put(44L, new ProductSeedData("Timex T80 Rose Gold Digital Women's Watch", "Vintage 1980s retro digital watch for women in rose gold stainless steel featuring INDIGLO night-light and expansion mesh band.", 5495.00, 18, "Women", "Digital", "https://timex.com/cdn/shop/files/TW2U93900_1000x.png"));
        seeds.put(45L, new ProductSeedData("Sonata SF Women's Active Grey Digital Watch", "Sporty women's digital watch with light grey resin strap, positive LCD digital dial, daily alarm, and water resistance.", 1299.00, 35, "Women", "Digital", "https://www.sonatawatches.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw3d13264b/images/Sonata/Catalog/87012PP04_1.jpg?sw=600&sh=600"));
        seeds.put(46L, new ProductSeedData("Sonata SF Women's Pink Digital Watch", "Vibrant pink sporty digital watch for women with round LCD screen, chime, stopwatch, and flexible silicone band.", 1399.00, 24, "Women", "Digital", "https://www.sonatawatches.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Sonata/Catalog/87012PP01_1.jpg"));
        seeds.put(47L, new ProductSeedData("Sonata SF Women's Purple Digital Watch", "Charming purple digital watch for active women featuring high-contrast numeric display, alarm, and water resistance.", 1399.00, 22, "Women", "Digital", "https://www.sonatawatches.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Sonata/Catalog/87012PP03_1.jpg"));
        seeds.put(48L, new ProductSeedData("Casio Women's Rose Gold Accent Digital Watch LW-204-1A", "Chic women's digital watch featuring a rose gold mirror frame, black resin case and band, LED backlight, and 50m water resistance.", 2495.00, 26, "Women", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/L/LW/LW2/LW-204-1A/assets/LW-204-1A.png.transform/main-visual-sp/image.png"));
        seeds.put(49L, new ProductSeedData("Casio Women's Sleek Black Minimalist Digital Watch LW-204-1B", "Sleek all-black minimalist women's digital watch with negative LCD display, countdown timer, auto-calendar, and water resistance.", 2495.00, 25, "Women", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/L/LW/LW2/LW-204-1B/assets/LW-204-1B.png.transform/main-visual-sp/image.png"));
        seeds.put(50L, new ProductSeedData("Casio Vintage Translucent White Digital Watch F-91WS-7", "Iconic vintage digital timepiece in trendy translucent white resin featuring alarm, stopwatch, and clean LCD display.", 1695.00, 30, "Women", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/F/F9/F91/F-91WS-7/assets/F-91WS-7.png.transform/main-visual-sp/image.png"));

        // Women - Luxury (10)
        seeds.put(81L, new ProductSeedData("Chopard Happy Sport Diamond Women's Watch", "Haute horlogerie luxury timepiece featuring free-floating dancing diamonds between two sapphire crystals.", 780000.00, 12, "Women", "Luxury", "images/luxury/chopard_happy_sport.jpg"));
        seeds.put(82L, new ProductSeedData("Breguet Reine de Naples Luxury Diamond Watch", "Ovoid-shaped haute horlogerie creation with mother-of-pearl dial, diamond-set bezel, and open-heart balance.", 1850000.00, 8, "Women", "Luxury", "images/luxury/breguet_reine_de_naples.jpg"));
        seeds.put(83L, new ProductSeedData("Titan Nebula Ashvi", "Solid 18K pink gold luxury women's watch with genuine diamond indices and sapphire crystal.", 545999.00, 30, "Women", "Luxury", "https://ik.imagekit.io/cyberstack/cyberstack/L7.webp"));
        seeds.put(84L, new ProductSeedData("Tissot SRV", "Rose gold tone luxury women's timepiece with mother of pearl dial and diamond hour markers.", 51999.00, 40, "Women", "Luxury", "https://ik.imagekit.io/cyberstack/cyberstack/L8.webp"));
        seeds.put(85L, new ProductSeedData("Movado Museum Classic Women's Watch 0607630", "Iconic single concave dot black museum dial with stainless steel bracelet, Swiss quartz.", 97125.00, 25, "Women", "Luxury", "https://ik.imagekit.io/Cyberstack/Movado%20Museum%20Classic%20Women's%20Watch%200607630.webp?updatedAt=1786171907793"));
        seeds.put(86L, new ProductSeedData("Cartier Tank Must de Cartier Watch", "Iconic rectangular luxury watch featuring Roman numerals, blued-steel sword hands, and sapphire cabochon crown.", 345000.00, 18, "Women", "Luxury", "images/luxury/cartier_tank_must.jpg"));
        seeds.put(87L, new ProductSeedData("Longines DolceVita Rose Gold & Steel Watch", "Classic rectangular Swiss luxury watch with silver flinqué dial, blued hands, and 18K rose gold accents.", 195000.00, 22, "Women", "Luxury", "images/luxury/longines_dolcevita.jpg"));
        seeds.put(88L, new ProductSeedData("Jaeger-LeCoultre Reverso Classic Small", "Art Deco reversible rectangular luxury timepiece with silver guilloché dial and blued hands.", 585000.00, 11, "Women", "Luxury", "https://ik.imagekit.io/Cyberstack/Extra%20photo/Jaeger-LeCoultre%20Unveil%20Reverso%202026%20Collection%20with%20Classic%20Small.jpg?updatedAt=1786176575047"));
        seeds.put(89L, new ProductSeedData("Rado Centrix Diamond Open Heart Women's Watch", "Master of materials high-tech ceramic Swiss watch featuring diamond hour markers and skeletonized dial.", 245000.00, 16, "Women", "Luxury", "images/luxury/rado_centrix_diamond.jpg"));
        seeds.put(90L, new ProductSeedData("Omega Constellation Quartz 28mm Diamond Watch", "Exquisite Swiss luxury timepiece with mother-of-pearl dial, diamond indices, and iconic Constellation claws.", 425000.00, 15, "Women", "Luxury", "images/luxury/omega_constellation_28.jpg"));

        // Women - Sports (10)
        seeds.put(121L, new ProductSeedData("Tissot PR100 Sports Chic", "Mother-of-pearl dial luxury sports chic watch", 57999.00, 28, "Women", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/L10.webp"));
        seeds.put(122L, new ProductSeedData("Carlington Velocity series", "Translucent pink resin sporty timepiece", 1699.00, 35, "Women", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/S6.webp"));
        seeds.put(123L, new ProductSeedData("Casio G-shock GMA-S2100-4AER", "Compact octagonal blush pink women's watch", 9999.00, 46, "Women", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/S7.jpg"));
        seeds.put(124L, new ProductSeedData("Casio G-shock GM-S2110-1A1", "Metal bezel compact women's tough watch", 14999.00, 54, "Women", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/S8.jpg"));
        seeds.put(125L, new ProductSeedData("Fastrack astor FR2 pro", "Rose gold metallic link smart fitness tracker", 3499.00, 50, "Women", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/S10.webp"));
        seeds.put(126L, new ProductSeedData("Garmin Lily 2 Sport Edition Smartwatch", "Petite and stylish women's sports smartwatch with patterned lens, hidden touchscreen, advanced health monitoring, and fitness tracking.", 24990.00, 15, "Women", "Sports", "images/sports/women/garmin_lily2_sport.jpg"));
        seeds.put(127L, new ProductSeedData("Polar Ignite 3 Titanium GPS Fitness Watch", "Premium GPS fitness and wellness watch for women featuring curved AMOLED display, precision optical heart rate tracking, and Nightly Recharge recovery insights.", 32990.00, 12, "Women", "Sports", "images/sports/women/polar_ignite3_titanium.jpg"));
        seeds.put(128L, new ProductSeedData("Suunto 5 Peak All Black Multisport GPS Watch", "Ultra-lightweight and durable GPS multisport watch for women with turn-by-turn route navigation, adaptive training guidance, and over 80 sport modes.", 29990.00, 14, "Women", "Sports", "images/sports/women/suunto_5_peak.jpg"));
        seeds.put(129L, new ProductSeedData("Casio Baby-G BGA-280-4A Pastel Pink Sports Watch", "Shock-resistant active sports watch for women featuring coral pink layered round case, metallic indices, 100m water resistance, world time, and LED illumination.", 7495.00, 25, "Women", "Sports", "images/sports/women/casio_babyg_bga280.jpg"));
        seeds.put(130L, new ProductSeedData("Timex Ironman Transit 33mm Women's Athletic Digital Watch", "Athletic women's digital sports watch featuring 10-lap stopwatch memory, countdown timer, customizable alarm, INDIGLO night-light, and 100m water resistance.", 4995.00, 30, "Women", "Sports", "images/sports/women/timex_ironman_transit.jpg"));

        // 2. MEN COLLECTION -> Men - Analog (10)
        seeds.put(11L, new ProductSeedData("Titan Neo", "Contemporary men's analog watch with a sophisticated dial and versatile everyday design.", 4599.00, 15, "Men", "Analog", "https://images.unsplash.com/photo-1522312346375-d1a52e2b99b3?q=80&w=600&auto=format&fit=crop"));
        seeds.put(12L, new ProductSeedData("Daniel Hechter Bercy", "Elegant men's analog watch with understated styling suitable for casual and formal wear.", 2799.00, 34, "Men", "Analog", "https://ik.imagekit.io/cyberstack/cyberstack/A5.jpg?updatedAt=1785159536791"));
        seeds.put(13L, new ProductSeedData("Timex Classic Men's Analog", "Timeless men's analog watch featuring a clean round dial and classic styling.", 2595.00, 27, "Men", "Analog", "https://ik.imagekit.io/Cyberstack/Extra%20photo/Timex%20Men%20Rose%20Gold%20Round%20Dial%20Analog%20Watch%20-%20TW0TG7629.webp?updatedAt=1786173696728"));
        seeds.put(14L, new ProductSeedData("Citizen Tsuyosa Men", "Premium automatic analog watch with an integrated bracelet and distinctive dial.", 35000.00, 20, "Men", "Analog", "https://ik.imagekit.io/Cyberstack/Citizen%20Tsuyosa%20Men.webp?updatedAt=1786171907857"));
        seeds.put(15L, new ProductSeedData("Fossil Neutra Men's Chronograph", "Modern analog chronograph featuring multiple sub-dials and sophisticated styling.", 13495.00, 22, "Men", "Analog", "https://ik.imagekit.io/Cyberstack/Fossil%20Neutra%20Men%20s%2044%20mm%20Chronograph%20Quartz%20Watch%20Blue%20Dial%20with%20Grey%20Stainless%20Steel%20Strap%20(FS6111).jpg?updatedAt=1786171824763"));
        seeds.put(16L, new ProductSeedData("Timex Waterbury Traditional Analog Watch", "Heritage-inspired men's analog watch featuring a rich blue sunburst dial, stainless steel case, date window, and classic solid bracelet.", 11995.00, 20, "Men", "Analog", "https://cdn.shopify.com/s/files/1/0787/5375/9521/files/TW2Y18600_9f83f369-6ea5-466a-a575-2142d529d9b6.jpg?v=1776950173"));
        seeds.put(17L, new ProductSeedData("Fossil Machine Chronograph Black Watch", "Bold 42mm men's analog chronograph watch featuring an industrial knurled bezel, dark textured dial with sub-dials, and black-plated stainless steel bracelet.", 12495.00, 18, "Men", "Analog", "https://fossil.scene7.com/is/image/FossilPartners/FS4775_main?$sfcc_fos_large$"));
        seeds.put(18L, new ProductSeedData("Casio Edifice Classic Chronograph EFR-526D-1AV", "High-performance men's analog chronograph watch boasting 1/10-second precision stopwatch, solid stainless steel band, black dial with silver indices, and 100m water resistance.", 8995.00, 24, "Men", "Analog", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/E/EF/EFR/EFR-526D-1AV/assets/EFR-526D-1AV_Seq1.png"));
        seeds.put(19L, new ProductSeedData("Seiko 5 Sports Automatic Analog Watch SRPD55K1", "Legendary automatic men's analog sports watch featuring Calibre 4R36 movement, unidirectional rotating bezel, Lumibrite markers, and day-date display with 100m water resistance.", 25000.00, 15, "Men", "Analog", "https://www.seikowatches.com/in-en/-/media/Images/Product--Image/All/Seiko/2022/02/20/02/14/SRPD55K1/SRPD55K1.png"));
        seeds.put(20L, new ProductSeedData("Citizen Eco-Drive Chandler Field Analog Watch", "Rugged military-inspired men's analog field watch powered by light via Eco-Drive technology, featuring a clean black dial with luminous Arabic numerals, day-date window, and durable green canvas strap.", 15900.00, 17, "Men", "Analog", "https://citizenwatch.widen.net/content/gzoliud0hm/webp"));

        // Men - Digital (10)
        seeds.put(51L, new ProductSeedData("Casio Vintage A159WA-N1DF Stainless Steel Watch", "Classic unisex vintage digital watch featuring silver stainless steel band, daily alarm, hourly time signal, and auto-calendar.", 1999.00, 57, "Men", "Digital", "https://ik.imagekit.io/cyberstack/cyberstack/D2.jpg"));
        seeds.put(52L, new ProductSeedData("Casio Youth AE-1200WH-1AV World Time Digital Watch", "Iconic world time digital watch featuring 10-year battery life, 100m water resistance, world map display, and 5 daily alarms.", 2695.00, 49, "Men", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/A/AE/AE1/AE-1200WH-1AV/assets/AE-1200WH-1AV_Seq1.jpg"));
        seeds.put(53L, new ProductSeedData("Casio F-91W-1DG Classic Iconic Digital Watch", "The timeless classic digital watch featuring daily alarm, hourly time signal, 1/100-second digital stopwatch, and 7-year battery life.", 1195.00, 60, "Men", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/F/F9/F91/F-91W-1/assets/F-91W-1_Seq1.jpg"));
        seeds.put(54L, new ProductSeedData("Casio Youth AE-1000W-1AV 10-Year Battery Digital Watch", "Aviator cockpit inspired digital watch with 10-year battery, 100m water resistance, world time in 48 cities, and LC analog display.", 2295.00, 35, "Men", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/A/AE/AE1/AE-1000W-1AV/assets/AE-1000W-1AV_Seq1.jpg"));
        seeds.put(55L, new ProductSeedData("Casio Heavy Duty HDC-700-1AV 100M Digital Watch", "Rugged heavy duty sports digital watch featuring 100m water resistance, front LED light button, 10-year battery, and Telememo 30.", 3295.00, 25, "Men", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/H/HD/HDC/HDC-700-1AV/assets/HDC-700-1AV_Seq1.jpg"));
        seeds.put(56L, new ProductSeedData("Casio Youth AE-1500WH-1AV Wide Display Digital Watch", "High-visibility large LCD digital watch featuring big digits, 10-year battery, 100m water resistance, and dual time.", 2995.00, 22, "Men", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/A/AE/AE1/AE-1500WH-1AV/assets/AE-1500WH-1AV_Seq1.jpg"));
        seeds.put(57L, new ProductSeedData("Casio Vintage A168WA-1 ElectroLuminescence Digital", "Classic unisex vintage digital watch featuring EL blue backlight, stainless steel bracelet, 1/100-second stopwatch, and daily alarm.", 2695.00, 30, "Men", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/A/A1/A16/A168WA-1/assets/A168WA-1_Seq1.jpg"));
        seeds.put(58L, new ProductSeedData("Casio Vintage A168WGG-1B Gunmetal Chrome Digital", "Sleek gunmetal ion-plated vintage digital watch with negative display, EL backlight, and dark stainless steel link band.", 3495.00, 24, "Men", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/A/A1/A16/A168WGG-1B/assets/A168WGG-1B_Seq1.jpg"));
        seeds.put(59L, new ProductSeedData("Casio Vintage A158WA-1 Stainless Steel Watch", "Everyday vintage digital stainless steel timepiece with micro-light, daily alarm, auto-calendar, and classic square case.", 1695.00, 40, "Men", "Digital", "https://www.casio.com/content/dam/casio/product-info/locales/in/en/timepiece/product/watch/A/A1/A15/A158WA-1/assets/A158WA-1_Seq1.jpg"));
        seeds.put(60L, new ProductSeedData("Timex T80 Retro Silver Expansion Digital Watch", "Retro classic 1980s digital watch with silver-tone stainless steel expansion band, INDIGLO night-light, and date display.", 4495.00, 20, "Men", "Digital", "https://cdn.shopify.com/s/files/1/0787/5375/9521/files/TW2R79300.jpg"));

        // Men - Luxury (10)
        seeds.put(91L, new ProductSeedData("Gosasa Luxury Hollowed Men's Watch", "Fashion skeleton mechanical watch", 7999.00, 38, "Men", "Luxury", "https://ik.imagekit.io/cyberstack/cyberstack/A3.jpg?updatedAt=1785159536501"));
        seeds.put(92L, new ProductSeedData("Mathey-Tissot MathyIII", "Swiss made precision watch", 15999.00, 49, "Men", "Luxury", "https://ik.imagekit.io/cyberstack/cyberstack/A6.jpg?updatedAt=1785159536610"));
        seeds.put(93L, new ProductSeedData("Parmigiani Fleurier Tonda PF skeleton", "Haute horlogerie openworked luxury watch", 508229.00, 60, "Men", "Luxury", "https://ik.imagekit.io/cyberstack/cyberstack/L1.webp"));
        seeds.put(94L, new ProductSeedData("Rado Captain cook", "Vintage-inspired automatic diver's watch", 59999.00, 40, "Men", "Luxury", "https://ik.imagekit.io/cyberstack/cyberstack/L2.webp"));
        seeds.put(95L, new ProductSeedData("Hublot Spirit of Bigbang Black", "All-black tonneau luxury chronograph", 59999.00, 35, "Men", "Luxury", "https://ik.imagekit.io/cyberstack/cyberstack/L4.webp"));
        seeds.put(96L, new ProductSeedData("Rado Captain Cook High-Tech Ceramic", "Plasma high-tech ceramic diver's watch", 355000.00, 20, "Men", "Luxury", "https://ik.imagekit.io/Cyberstack/Rado%20Captain%20Cook%203210%20Blue%20Dial,%20High-Tech%20Ceramic,%2042.0%20mm,%20Automatic.webp?updatedAt=1786171907129"));
        seeds.put(97L, new ProductSeedData("Vacheron Constantin Overseas Perpetual Calendar", "Ultra-thin skeleton perpetual calendar", 11550000.00, 9, "Men", "Luxury", "https://ik.imagekit.io/Cyberstack/Extra%20photo/Vacheron%20Constantin%20Overseas%20Perpetual%20Calendar%20Skeleton.webp?updatedAt=1786176575421"));
        seeds.put(98L, new ProductSeedData("Audemars Piguet Royal Oak Tourbillon", "Openworked selfwinding flying tourbillon", 18500000.00, 12, "Men", "Luxury", "https://ik.imagekit.io/Cyberstack/Extra%20photo/A%20New%20Selfwinding%20Flying%20Tourbillon%20Openworked.jpg?updatedAt=1786176575263"));
        seeds.put(99L, new ProductSeedData("Grand Seiko Heritage Snowflake SBGA211G", "Spring Drive snowflake textured dial watch", 525000.00, 22, "Men", "Luxury", "https://ik.imagekit.io/Cyberstack/Extra%20photo/Grand%20Seiko%20Heritage%20Collection%20Snowflake%20SBGA211G.jpg?updatedAt=1786176575149"));
        seeds.put(100L, new ProductSeedData("Patek Philippe Nautilus", "Legendary steel luxury sports timepiece", 8500000.00, 13, "Men", "Luxury", "https://ik.imagekit.io/Cyberstack/Extra%20photo/Patek%20Philippe%20Nautilus.webp?updatedAt=1786176574628"));

        // Men - Sports (10)
        seeds.put(131L, new ProductSeedData("Casio G-Shock GA-700-7A", "Rugged shock-resistant sports watch", 11999.00, 59, "Men", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/A7.jpg?updatedAt=1785159536892"));
        seeds.put(132L, new ProductSeedData("North Edge ALPS", "Rugged digital altitude outdoor watch", 7999.00, 76, "Men", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/D1.jpg"));
        seeds.put(133L, new ProductSeedData("Fastrack Xtreme Adventure", "Rugged multisport adventure smartwatch", 6499.00, 19, "Men", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/S1.webp"));
        seeds.put(134L, new ProductSeedData("Omega speed master", "Iconic Moonwatch black ceramic chronograph", 65499.00, 26, "Men", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/S2.webp"));
        seeds.put(135L, new ProductSeedData("Alpina Alpiner Extreme skeleton", "High-performance skeletonized sports watch", 39999.00, 40, "Men", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/S5.webp"));
        seeds.put(136L, new ProductSeedData("Certina DS PH200M Powermatic 80", "Heritage 1960s reissue automatic diver", 82000.00, 19, "Men", "Sports", "https://ik.imagekit.io/Cyberstack/Men%20Automatic%20DS%20PH200M%20Powermatic%2080%20watch%20by%20Certina.png?updatedAt=1786171843157"));
        seeds.put(137L, new ProductSeedData("Fossil FS5889 Retro Analog-Digital Watch", "Dual display retro sporty watch", 11495.00, 4, "Men", "Sports", "https://ik.imagekit.io/Cyberstack/Extra%20photo/Fossil%20FS5889%20Retro%20Analog-Digital%20Watch%20for%20Men.avif?updatedAt=1786173696645"));
        seeds.put(138L, new ProductSeedData("Polar Grit X Pro", "Military-grade outdoor multisport watch", 49990.00, 16, "Men", "Sports", "https://ik.imagekit.io/Cyberstack/Extra%20photo/Polar%20Grit%20X%20Pro.png?updatedAt=1786183408700"));
        seeds.put(139L, new ProductSeedData("Coros Vertix 2", "Dual frequency extreme adventure GPS watch", 69990.00, 9, "Men", "Sports", "https://ik.imagekit.io/Cyberstack/Extra%20photo/Coros%20Vertix%202.webp?updatedAt=1786183408616"));
        seeds.put(140L, new ProductSeedData("Garmin Instinct 2 Solar", "Unlimited solar powered tactical outdoor watch", 39990.00, 10, "Men", "Sports", "https://ik.imagekit.io/Cyberstack/Extra%20photo/Garmin%20Instinct%202%20Solar.jpg?updatedAt=1786183408590"));

        // 3. KIDS COLLECTION -> Kids - Analog (10)
        seeds.put(21L, new ProductSeedData("Zoop Kids Blue Analog Watch", "Bright blue children's analog watch with clear hour markers and comfortable strap.", 995.00, 14, "Kids", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw99ac0a99/images/Titan/Catalog/26019PP30W_1.jpg"));
        seeds.put(22L, new ProductSeedData("Zoop Kids Pink Analog Watch", "Colourful pink analog watch with a simple readable dial designed for young wearers.", 995.00, 15, "Kids", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw154af053/images/Titan/Catalog/C4008PP01_1.jpg"));
        seeds.put(23L, new ProductSeedData("V2A Critter Kids Analog Watch", "Fun children's analog watch featuring a playful design and easy-to-read time display.", 899.00, 40, "Kids", "Analog", "https://ik.imagekit.io/cyberstack/cyberstack/D7.jpg"));
        seeds.put(24L, new ProductSeedData("Timex Youth Analog Watch", "Kid-friendly analog watch with an easy-to-read dial and comfortable strap.", 1495.00, 42, "Kids", "Analog", "https://timex.com/cdn/shop/files/TW2W92100_6353ddbc-6515-42aa-b984-d9f8226792be.png"));
        seeds.put(25L, new ProductSeedData("Fastrack Kids Analog Watch", "Colourful analog watch with youthful styling and a durable everyday design.", 2195.00, 35, "Kids", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Fastrack/Catalog/9915PP88_1.jpg"));
        seeds.put(26L, new ProductSeedData("Zoop Kids Orange Analog Watch", "Fun and vibrant children's analog watch with an easy-to-read dial, bright orange case and strap, and skin-friendly durable materials.", 995.00, 25, "Kids", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Titan/Catalog/C4008PP02_1.jpg"));
        seeds.put(27L, new ProductSeedData("Zoop Kids Purple Analog Watch", "Charming purple analog watch designed for young learners, featuring clear numeral markings, soft silicone strap, and water resistance for everyday play.", 995.00, 25, "Kids", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Titan/Catalog/C4008PP03_1.jpg"));
        seeds.put(28L, new ProductSeedData("Zoop Kids Yellow Analog Watch", "Cheerful yellow children's analog watch with bold contrasting hands, durable acrylic crystal, and a comfortable polyurethane strap tailored for small wrists.", 995.00, 22, "Kids", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Titan/Catalog/26019PP02_1.jpg"));
        seeds.put(29L, new ProductSeedData("Sonata Kids Fun Analog Watch", "Sturdy and colorful children's analog timepiece featuring a clean dial with distinct hour and minute indicators and a flexible resin band.", 899.00, 28, "Kids", "Analog", "https://www.sonatawatches.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dwb91c9c05/images/Sonata/Catalog/77122PP02_1.jpg"));
        seeds.put(30L, new ProductSeedData("Fastrack Play Kids Analog Watch", "Youthful sporty analog watch for kids with high-contrast markings, lightweight ergonomic case, and durable strap for active play.", 1295.00, 24, "Kids", "Analog", "https://www.fastrack.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw8ae0ce75/images/Fastrack/Catalog/38024PP25_1.jpg"));

        // Kids - Digital (10)
        seeds.put(61L, new ProductSeedData("Zoop Kids Black Digital Watch", "Durable black digital watch for kids by Titan Zoop featuring clear numeric LCD display, dual time, stopwatch, and splash resistance.", 995.00, 50, "Kids", "Digital", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Titan/Catalog/C4003PP03_1.jpg"));
        seeds.put(62L, new ProductSeedData("eYotto Fashion Kids Digital Watch", "Vibrant digital kids watch with multi-color LED backlight, alarm, and comfortable resin strap.", 299.00, 28, "Kids", "Digital", "https://ik.imagekit.io/cyberstack/cyberstack/D5.jpg"));
        seeds.put(63L, new ProductSeedData("Zoop Kids Blue Digital LCD Watch", "Vibrant blue kids digital watch by Titan Zoop featuring easy-to-read digits, lightweight casing, alarm, and comfortable polyurethane strap.", 995.00, 38, "Kids", "Digital", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Titan/Catalog/C4008PP01_1.jpg"));
        seeds.put(64L, new ProductSeedData("V2A Cute Girls Digital Pastel Watch", "Pastel pink digital watch for young girls with clear LCD time display, alarm, and water resistance.", 699.00, 47, "Kids", "Digital", "https://ik.imagekit.io/cyberstack/cyberstack/D8.jpg"));
        seeds.put(65L, new ProductSeedData("Sonata SF Digital Youth Watch", "Sporty active digital kids watch with bold digital numbers, daily alarm, and durable silicone strap.", 999.00, 50, "Kids", "Digital", "https://ik.imagekit.io/cyberstack/cyberstack/D9.jpg"));
        seeds.put(66L, new ProductSeedData("ON TIME OCTUS Illuminated Digital Kids Watch", "Illuminated display digital kids watch with 7-color backlight, stopwatch, and shock-resistant casing.", 599.00, 40, "Kids", "Digital", "https://ik.imagekit.io/cyberstack/cyberstack/D10.jpg"));
        seeds.put(67L, new ProductSeedData("Zoop Kids Pink Digital Watch", "Vibrant pink digital watch by Titan Zoop for girls with large readable digits, soft polyurethane strap, and splash resistance.", 995.00, 30, "Kids", "Digital", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Titan/Catalog/C4008PP02_1.jpg"));
        seeds.put(68L, new ProductSeedData("Zoop Kids Orange Digital Watch", "Fun and sporty orange digital watch for kids by Titan Zoop featuring stopwatch, daily alarm, and rugged casing.", 995.00, 25, "Kids", "Digital", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Titan/Catalog/C4008PP03_1.jpg"));
        seeds.put(69L, new ProductSeedData("Sonata SF Kids Red Digital Watch", "Bright red active digital kids watch featuring high-contrast LCD screen, daily alarm, and durable resin construction.", 899.00, 35, "Kids", "Digital", "https://www.sonatawatches.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw83a71b12/images/Sonata/Catalog/77085PP02_1.jpg?sw=600&sh=600"));
        seeds.put(70L, new ProductSeedData("Sonata SF Kids Blue Digital Watch", "Cool ocean blue digital kids watch with easy-to-read numbers, stopwatch, daily chime, and water resistance.", 899.00, 30, "Kids", "Digital", "https://www.sonatawatches.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Sonata/Catalog/77085PP01_1.jpg"));

        // Kids - Luxury (10)
        seeds.put(101L, new ProductSeedData("Flik Flak Swiss Made Magical Unicorn Children's Watch", "Swiss-made luxury children's timepiece featuring a bio-sourced pink case, pastel dial, color-coded learning hands, and washable fabric strap.", 4450.00, 25, "Kids", "Luxury", "images/luxury/kids/flik_flak_magical_unicorn.jpg"));
        seeds.put(102L, new ProductSeedData("Flik Flak Swiss Made Space Trip Galaxy Children's Watch", "Swiss-crafted luxury boys watch featuring a deep cosmic blue dial, planetary learning hands, and recycled PET patterned strap.", 4450.00, 22, "Kids", "Luxury", "images/luxury/kids/flik_flak_space_trip.jpg"));
        seeds.put(103L, new ProductSeedData("Lacoste 12.12 Kids Navy Blue Silicone Designer Watch", "Iconic French designer kids watch featuring signature petit piqué navy silicone strap, white index markers, and green embroidered crocodile motif.", 7250.00, 18, "Kids", "Luxury", "images/luxury/kids/lacoste_kids_1212_navy.jpg"));
        seeds.put(104L, new ProductSeedData("Lacoste 12.12 Kids Pastel Pink Silicone Designer Watch", "Chic designer girls watch with soft blush pink resin case, textured silicone band, and signature Lacoste crocodile emblem.", 7250.00, 20, "Kids", "Luxury", "images/luxury/kids/lacoste_kids_1212_pink.jpg"));
        seeds.put(105L, new ProductSeedData("Tommy Hilfiger Junior Sport White Multi-Eye Watch", "Premium American designer youth watch featuring a clean white dial, iconic red-white-blue flag sub-eyes, and flexible silicone strap.", 6950.00, 16, "Kids", "Luxury", "images/luxury/kids/tommy_hilfiger_junior_white.jpg"));
        seeds.put(106L, new ProductSeedData("Tommy Hilfiger Junior Navy Blue Flag Dial Watch", "Classic designer youth timepiece with rich navy blue dial, distinctive Hilfiger stripes, Arabic hour numerals, and stainless steel case.", 6500.00, 19, "Kids", "Luxury", "images/luxury/kids/tommy_hilfiger_junior_navy.jpg"));
        seeds.put(107L, new ProductSeedData("Scuderia Ferrari RedRev Youth Racing Watch", "High-octane Italian luxury motorsport youth watch featuring Rosso Corsa red dial, honeycomb texture, and iconic Prancing Horse shield.", 8450.00, 14, "Kids", "Luxury", "images/luxury/kids/ferrari_redrev_youth_red.jpg"));
        seeds.put(108L, new ProductSeedData("Titan Zoop Special Edition Rose Gold Girls Watch", "Celebratory designer girls watch featuring polished rose gold bezel, shimmering sunburst dial, and skin-safe floral motif band.", 3495.00, 24, "Kids", "Luxury", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Titan/Catalog/26019PP04_1.jpg"));
        seeds.put(109L, new ProductSeedData("Fastrack Play Designer Geometric Youth Watch", "Contemporary designer youth analog watch with striking geometric dial, lightweight ergonomic case, and durable strap.", 2995.00, 20, "Kids", "Luxury", "https://www.fastrack.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Fastrack/Catalog/38024PP03_1.jpg"));
        seeds.put(110L, new ProductSeedData("Fastrack Stunners Designer Sport Youth Watch", "High-energy designer youth sport watch with high-contrast dual-tone markings, impact-resistant case, and flexible strap.", 3295.00, 22, "Kids", "Luxury", "https://www.fastrack.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Fastrack/Catalog/38024PP05_1.jpg"));

        // Kids - Sports (10)
        seeds.put(141L, new ProductSeedData("Zoop Dual-Tone Rugged Sport Kids Watch", "Rugged shock-resistant dual-tone digital-analog sports watch for kids with stopwatch, alarm, and 50m water resistance.", 1495.00, 25, "Kids", "Sports", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/images/Titan/Catalog/26019PP02_1.jpg"));
        seeds.put(142L, new ProductSeedData("GOBALT Mustang Stalliam", "Smart outdoor active fitness watch", 4999.00, 46, "Kids", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/S4.webp"));
        seeds.put(143L, new ProductSeedData("V2A dual time analog digital sports watch", "Dual-time zone sporty kids watch", 1199.00, 36, "Kids", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/S9.jpg"));
        seeds.put(144L, new ProductSeedData("Fastrack Reflex Play Active Junior Sport Watch", "Smart-look sporty youth fitness watch with multi-sport tracking, daily activity goals, and skin-friendly silicone band.", 2195.00, 20, "Kids", "Sports", "https://www.fastrack.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw8ae0ce75/images/Fastrack/Catalog/38024PP25_1.jpg"));
        seeds.put(145L, new ProductSeedData("SKMEI Waterproof Shockproof Outdoor Kids Sports Watch", "Tough shockproof outdoor sports digital watch for boys and girls featuring camouflage bezel, LED backlight, and 50m water resistance.", 1299.00, 30, "Kids", "Sports", "https://ik.imagekit.io/cyberstack/cyberstack/D10.jpg"));
        seeds.put(146L, new ProductSeedData("Garmin Vivofit Jr. 3 Kids Sports Activity Tracker Watch", "Swim-friendly kids activity fitness tracker featuring vibrant color display, timed sports activities, parent-managed chore tasks, and 1-year battery life.", 7990.00, 20, "Kids", "Sports", "images/sports/kids/garmin_vivofit_jr3.jpg"));
        seeds.put(147L, new ProductSeedData("Fastrack Play Active Multi-Sport Youth Watch", "High-energy youth athletic sports watch with impact-resistant resin case, high-contrast dual-tone dial, and flexible silicone strap for active play.", 1995.00, 25, "Kids", "Sports", "images/sports/kids/fastrack_play_active_sport.jpg"));
        seeds.put(148L, new ProductSeedData("Sonata SF Kids Digital Camouflage Sports Watch", "Active outdoor camouflage digital watch for kids featuring round rugged bezel, multi-function digital alarm, countdown stopwatch, and durable green resin strap.", 1199.00, 30, "Kids", "Sports", "images/sports/kids/sonata_sf_kids_camo.jpg"));
        seeds.put(149L, new ProductSeedData("Titan Zoop Dual-Time Rugged Kids Sports Watch", "Vibrant and tough outdoor sports watch for kids featuring dual-time analog-digital display, 1/100-second stopwatch, EL backlight, and 30m water resistance.", 1495.00, 24, "Kids", "Sports", "images/sports/kids/zoop_rugged_sports_red.jpg"));
        seeds.put(150L, new ProductSeedData("Titan Zoop Splashproof Digital-Analog Kids Sports Watch", "Ergonomic multi-sport kids watch with dual-dial timekeeping, luminous hands, scratch-resistant acrylic crystal, and skin-friendly silicone band.", 1395.00, 28, "Kids", "Sports", "images/sports/kids/zoop_splashproof_digital_analog.jpg"));

        // 4. COUPLES COLLECTION -> Couples - Analog (10)
        seeds.put(31L, new ProductSeedData("Sonata Couple Analog Watch Set", "Affordable matching analog watches designed with coordinated styling for couples.", 3499.00, 36, "Couples", "Analog", "https://www.sonatawatches.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dwfb20cd13/images/Sonata/Catalog/7712787046SM01_1.jpg"));
        seeds.put(32L, new ProductSeedData("Titan Bandhan Couple Watch Set", "Coordinated his-and-her analog watches featuring matching elegant styling.", 7995.00, 48, "Couples", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw86c12e5f/images/Titan/Catalog/17672596KM01_1.jpg"));
        seeds.put(33L, new ProductSeedData("Titan Bandhan Premium Couple Set", "Premium matching analog watch pair with sophisticated finishing and elegant dials.", 12995.00, 19, "Couples", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dwac9a996f/images/Titan/Catalog/18062648YM01_1.jpg"));
        seeds.put(34L, new ProductSeedData("Fastrack Couple Analog Watch Set", "Youthful matching analog watch pair with contemporary styling for couples.", 4995.00, 30, "Couples", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw52aa0972/images/Fastrack/Catalog/3819268060QM02_1.jpg"));
        seeds.put(35L, new ProductSeedData("Timex Couple Analog Watch Set", "Matching analog watch pair with classic dials and complementary designs.", 5999.00, 27, "Couples", "Analog", "https://shop.timexindia.com/cdn/shop/files/TW00PR266_500x.jpg"));
        seeds.put(36L, new ProductSeedData("Timex Classic Blue Dial Couple Watch Set", "Elegant his-and-hers analog watch pair featuring coordinated deep blue sunburst dials, stainless steel cases, and matching multi-link silver bracelets.", 7995.00, 18, "Couples", "Analog", "https://cdn.shopify.com/s/files/1/0787/5375/9521/files/TW00PR337.jpg?v=1756872622"));
        seeds.put(37L, new ProductSeedData("Titan Bandhan Anthracite Couple Watch Set", "Refined his-and-hers matching pair featuring contemporary anthracite grey dials, polished two-tone accents, and precision analog quartz movements.", 11495.00, 16, "Couples", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw13b62e1e/images/Titan/Catalog/18062617NM01_1.jpg?sw=600&sh=600"));
        seeds.put(38L, new ProductSeedData("Titan Bandhan Classic Silver Couple Set", "Timeless celebratory his-and-hers watch set with classic silver dials, gold-tone Roman numeral indices, and polished stainless steel link bracelets.", 8995.00, 20, "Couples", "Analog", "https://www.titan.co.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dwc4d5db2f/images/Titan/Catalog/17332570KM01_1.jpg?sw=600&sh=600"));
        seeds.put(39L, new ProductSeedData("Sonata Wedding Gold Dial Couple Watch Set", "Traditional wedding couple watch set featuring coordinated champagne gold dials, classic leather straps, and dependable analog quartz movements.", 3299.00, 26, "Couples", "Analog", "https://www.sonatawatches.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw0d6020f1/images/Sonata/Catalog/7712787046YL01_1.jpg?sw=600&sh=600"));
        seeds.put(40L, new ProductSeedData("Fastrack Mixmatched Blue Dial Couple Watch Set", "Trendy his-and-hers analog watch pair featuring vibrant royal blue dials, modern case architecture, and durable silver-toned stainless steel straps.", 4995.00, 22, "Couples", "Analog", "https://www.fastrack.in/dw/image/v2/BKDD_PRD/on/demandware.static/-/Sites-titan-master-catalog/default/dw748d7149/images/Fastrack/Catalog/33056296SM02P_1.jpg?sw=600&sh=600"));

        // Couples - Digital (10)
        seeds.put(71L, new ProductSeedData("Casio Vintage Silver & Gold Couple Digital Set", "CUSTOM TIMEVERSE BUNDLE: TimeVerse custom digital couple bundle pairing Casio A168WA-1 with Casio LA670WGA-1.", 5190.00, 15, "Couples", "Digital", "images/couples/couple_bundle_1.jpg"));
        seeds.put(72L, new ProductSeedData("Casio Vintage Classic Silver Couple Digital Set", "CUSTOM TIMEVERSE BUNDLE: TimeVerse custom digital couple bundle pairing Casio A159WA-N1 with Casio LA670WA-1.", 3694.00, 20, "Couples", "Digital", "images/couples/couple_bundle_2.jpg"));
        seeds.put(73L, new ProductSeedData("Casio Vintage All-Gold Couple Digital Set", "CUSTOM TIMEVERSE BUNDLE: TimeVerse custom digital couple bundle pairing Casio A168WA-1 with Casio B640WC-5A.", 4990.00, 18, "Couples", "Digital", "images/couples/couple_bundle_3.jpg"));
        seeds.put(74L, new ProductSeedData("Timex T80 Two-Tone Silver & Rose Gold Couple Set", "CUSTOM TIMEVERSE BUNDLE: TimeVerse custom digital couple bundle pairing Timex T80 Silver TW2R79300 with Timex T80 Rose Gold TW2U93900.", 9990.00, 16, "Couples", "Digital", "images/couples/couple_bundle_4.jpg"));
        seeds.put(75L, new ProductSeedData("Casio Vintage Gunmetal & Silver Couple Digital Set", "CUSTOM TIMEVERSE BUNDLE: TimeVerse custom digital couple bundle pairing Casio A168WGG-1B with Casio LA670WA-1.", 5190.00, 16, "Couples", "Digital", "images/couples/couple_bundle_5.jpg"));
        seeds.put(76L, new ProductSeedData("Casio Vintage Minimalist Steel Couple Set", "CUSTOM TIMEVERSE BUNDLE: TimeVerse custom digital couple bundle pairing Casio A158WA-1 with Casio LA670WGA-1.", 3390.00, 24, "Couples", "Digital", "images/couples/couple_bundle_6.jpg"));
        seeds.put(77L, new ProductSeedData("Casio World Time & Rose Gold Active Couple Set", "CUSTOM TIMEVERSE BUNDLE: TimeVerse custom digital couple bundle pairing Casio AE-1200WH-1AV with Casio LW-204-1A.", 4990.00, 20, "Couples", "Digital", "images/couples/couple_bundle_7.jpg"));
        seeds.put(78L, new ProductSeedData("Sonata SF Active Sport Couple Digital Set", "CUSTOM TIMEVERSE BUNDLE: TimeVerse custom digital couple bundle pairing Sonata SF Men 77084PP01 with Sonata SF Women 87012PP04.", 2298.00, 22, "Couples", "Digital", "images/couples/couple_bundle_8.jpg"));
        seeds.put(79L, new ProductSeedData("Sonata SF Dual-Tone Pink & Blue Couple Set", "CUSTOM TIMEVERSE BUNDLE: TimeVerse custom digital couple bundle pairing Sonata SF Blue 77085PP01 with Sonata SF Pink 87012PP01.", 2298.00, 18, "Couples", "Digital", "images/couples/couple_bundle_9.jpg"));
        seeds.put(80L, new ProductSeedData("Casio Retro Minimalist Black & White Couple Set", "CUSTOM TIMEVERSE BUNDLE: TimeVerse custom digital couple bundle pairing Casio F-91W-1DG with Casio F-91WS-7.", 2890.00, 25, "Couples", "Digital", "images/couples/couple_bundle_10.jpg"));

        // Couples - Luxury (10)
        seeds.put(111L, new ProductSeedData("TimeVerse Curated Hublot Matching Luxury Couple Set", "Exclusive TimeVerse curated luxury pairing uniting the bold Hublot Spirit of Big Bang tonneau skeleton case with an elegant matching diamond-bezel Classic Fusion.", 3450000.00, 5, "Couples", "Luxury", "images/luxury/couples/hublot_couple_pair.jpg"));
        seeds.put(112L, new ProductSeedData("TimeVerse Curated Cartier Santos & Panthère His & Hers Luxury Pair", "Distinguished TimeVerse curated Parisian luxury pairing combining the iconic Santos de Cartier automatic in 18K yellow gold with the Panthère de Cartier diamond timepiece.", 2850000.00, 6, "Couples", "Luxury", "images/luxury/couples/cartier_santos_panthere_couple.jpg"));
        seeds.put(113L, new ProductSeedData("TimeVerse Curated A. Lange & Söhne Lange 1 & Little Lange 1 His & Hers Luxury Pair", "Masterpiece Saxon haute horlogerie pairing by TimeVerse featuring the asymmetrical Lange 1 in 18K white gold with matching Little Lange 1 guilloché dial.", 6200000.00, 4, "Couples", "Luxury", "images/luxury/couples/alange_sohne_lange1_couple.jpg"));
        seeds.put(114L, new ProductSeedData("TimeVerse Curated Rolex Datejust 36 & 31 Everose Gold His & Hers Pair", "TimeVerse curated luxury pair featuring 18K Everose gold fluted bezels, slate & sundust diamond dials, and Jubilee bracelets.", 2450000.00, 6, "Couples", "Luxury", "images/luxury/couples/rolex_datejust_couple.jpg"));
        seeds.put(115L, new ProductSeedData("TimeVerse Curated Patek Philippe Calatrava Timeless His & Hers Pair", "TimeVerse curated Swiss haute horlogerie elegance with hobnail guilloché bezels, silvery opaline dials, and hand-stitched alligator straps.", 4200000.00, 4, "Couples", "Luxury", "images/luxury/couples/patek_calatrava_couple.jpg"));
        seeds.put(116L, new ProductSeedData("TimeVerse Curated Jaeger-LeCoultre Reverso Tribute His & Hers Luxury Pair", "TimeVerse curated Art Deco reversible case pairing crafted in 18K pink gold and stainless steel with matching sunray dials.", 1850000.00, 5, "Couples", "Luxury", "images/luxury/couples/jlc_reverso_couple.jpg"));
        seeds.put(117L, new ProductSeedData("TimeVerse Curated Audemars Piguet Royal Oak His & Hers Luxury Pair", "TimeVerse curated integrated bracelet pair in stainless steel and 18K pink gold with signature Grande Tapisserie pattern dials.", 5600000.00, 3, "Couples", "Luxury", "images/luxury/couples/ap_royaloak_couple.jpg"));
        seeds.put(118L, new ProductSeedData("TimeVerse Curated Omega Constellation Master Chronometer His & Hers Pair", "TimeVerse curated pairing featuring half-moon facets and iconic bezel claws in 18K Sedna gold and stainless steel with diamond hour markers.", 1650000.00, 8, "Couples", "Luxury", "images/luxury/couples/omega_constellation_couple.jpg"));
        seeds.put(119L, new ProductSeedData("TimeVerse Curated Cartier Ballon Bleu Matching His & Hers Pair", "TimeVerse curated convex curved cases with integrated fluted crowns set with blue cabochons, Roman numerals, and two-tone 18K yellow gold bracelets.", 1950000.00, 6, "Couples", "Luxury", "images/luxury/couples/cartier_ballon_couple.jpg"));
        seeds.put(120L, new ProductSeedData("TimeVerse Curated Chopard Alpine Eagle & Happy Sport Duo Set", "TimeVerse curated his & hers duo combining Lucent Steel sports-luxe design with dancing diamonds on mother-of-pearl dial.", 2850000.00, 5, "Couples", "Luxury", "images/luxury/couples/chopard_alpine_couple.jpg"));

        // Couples - Sports (10)
        seeds.put(151L, new ProductSeedData("TimeVerse Curated Omega Seamaster Aqua Terra His & Hers Sports Luxury Pair", "TimeVerse curated luxury maritime sports pairing uniting the 41mm teak-concept blue dial Aqua Terra automatic with the matching 34mm diamond-indexed edition.", 1050000.00, 5, "Couples", "Sports", "images/sports/couples/omega_seamaster_aquaterra_couple.jpg"));
        seeds.put(152L, new ProductSeedData("Casio G-Shock Lovers Collection LOV-22A Matching Sports Pair", "Official Casio limited edition his-and-hers sports watch pair featuring tough shock resistance, 200m water resistance, and coordinated sporty styling.", 22995.00, 15, "Couples", "Sports", "images/sports/couples/casio_lov22a_couple.jpg"));
        seeds.put(153L, new ProductSeedData("TimeVerse Curated Garmin Adventure Multisport Couple Set", "TimeVerse curated his-and-hers outdoor GPS adventure duo pairing the rugged Instinct 2 Solar with the elegant Lily 2 Sport edition for active couples.", 64980.00, 8, "Couples", "Sports", "images/sports/couples/garmin_adventure_couple.jpg"));
        seeds.put(154L, new ProductSeedData("TimeVerse Curated Casio G-Shock & Baby-G Active Sports Pair", "Coordinated active sports couple pair featuring the iconic G-Shock GA-700 tough sports watch paired with the pastel Baby-G BGA-280 shock-resistant timepiece.", 19494.00, 12, "Couples", "Sports", "images/sports/couples/casio_gshock_babyg_couple.jpg"));
        seeds.put(155L, new ProductSeedData("TimeVerse Curated Polar Endurance Training Couple Set", "High-performance endurance training couple bundle combining the military-grade Polar Grit X Pro outdoor multisport watch with the lightweight Polar Ignite 3 Titanium.", 82980.00, 6, "Couples", "Sports", "images/sports/couples/polar_endurance_couple.jpg"));
        seeds.put(156L, new ProductSeedData("TimeVerse Curated Suunto Expedition Multisport GPS Pair", "Premium Nordic expedition sports pair uniting the ultra-durable Suunto Vertical adventure watch with the lightweight Suunto 5 Peak multisport GPS timepiece.", 89980.00, 5, "Couples", "Sports", "images/sports/couples/suunto_expedition_couple.jpg"));
        seeds.put(157L, new ProductSeedData("TimeVerse Curated TAG Heuer Aquaracer Professional His & Hers Diver Pair", "Prestigious Swiss luxury sports diving pair pairing the 43mm Aquaracer Professional 300 automatic with the 36mm mother-of-pearl diamond dive watch.", 485000.00, 4, "Couples", "Sports", "images/sports/couples/tag_heuer_aquaracer_couple.jpg"));
        seeds.put(158L, new ProductSeedData("TimeVerse Curated Tissot Seastar 1000 His & Hers Sports Diver Set", "Swiss-crafted high-performance watersports couple set featuring the 40mm Seastar 1000 automatic diver alongside the matching 36mm gradient dial sports edition.", 124000.00, 7, "Couples", "Sports", "images/sports/couples/tissot_seastar_couple.jpg"));
        seeds.put(159L, new ProductSeedData("TimeVerse Curated Fossil Active Sports Chronograph Couple Set", "Matching contemporary sports chronograph pair featuring the bold Fossil FS5889 retro ana-digi sports watch coordinated with an athletic silicone-strap companion.", 20990.00, 10, "Couples", "Sports", "images/sports/couples/fossil_active_couple.jpg"));
        seeds.put(160L, new ProductSeedData("TimeVerse Curated Timex Ironman Athletic Performance Couple Pair", "Dynamic athletic training his-and-hers couple pair pairing the Timex Ironman classic athletic chronograph with the 33mm Ironman Transit sport edition.", 9990.00, 14, "Couples", "Sports", "images/sports/couples/timex_ironman_couple.jpg"));

        return seeds;
    }
}
