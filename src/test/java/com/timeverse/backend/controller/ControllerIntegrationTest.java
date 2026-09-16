package com.timeverse.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.mockito.Mockito;
import com.timeverse.backend.entity.Address;
import com.timeverse.backend.dto.OrderResponse;
import com.timeverse.backend.service.OrderService;
import com.timeverse.backend.service.JwtService;
import static org.mockito.ArgumentMatchers.any;
import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest(properties = {
    "spring.mail.username=test-mail@gmail.com",
    "spring.mail.password=test-password"
})
@AutoConfigureMockMvc
public class ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.timeverse.backend.repository.CategoryRepository categoryRepository;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtService jwtService;

    @Test
    @WithMockUser
    public void testPlaceOrder_EndpointSuccess() throws Exception {
        Address mockAddress = Address.builder()
                .addressId(1L)
                .fullName("Soumya")
                .street("123 Luxury Lane")
                .city("Bespoke City")
                .zipCode("560001")
                .build();

        OrderResponse mockResponse = OrderResponse.builder()
                .orderId(10L)
                .userId(6L)
                .totalAmount(BigDecimal.valueOf(4599.00))
                .status("PLACED")
                .shippingAddress(mockAddress)
                .build();

        Mockito.when(jwtService.extractUserId(any())).thenReturn(6L);
        Mockito.when(jwtService.extractRole(any())).thenReturn("CUSTOMER");
        Mockito.when(orderService.placeOrder(6L, 1L, null))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/orders/place/6")
                .header("Authorization", "Bearer dummy-token")
                .param("addressId", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(10))
                .andExpect(jsonPath("$.shippingAddress.fullName").value("Soumya"))
                .andExpect(jsonPath("$.shippingAddress.street").value("123 Luxury Lane"));
    }

    @Test
    public void testGetProducts_Success() throws Exception {
        mockMvc.perform(get("/api/products")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    public void testGetCategories_Success() throws Exception {
        mockMvc.perform(get("/api/categories")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    public void testGetCategories_RestructuredStructure() throws Exception {
        mockMvc.perform(get("/api/categories")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.categoryName == 'Women')]").exists())
                .andExpect(jsonPath("$.data[?(@.categoryName == 'Men')]").exists())
                .andExpect(jsonPath("$.data[?(@.categoryName == 'Kids')]").exists())
                .andExpect(jsonPath("$.data[?(@.categoryName == 'Couples')]").exists());
    }


    @Test
    public void testGetCategories_Cors_VercelOrigin() throws Exception {
        mockMvc.perform(get("/api/categories")
                .header("Origin", "https://timeverse-xpo135qhj-time-verse1.vercel.app")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://timeverse-xpo135qhj-time-verse1.vercel.app"));
    }

    @Test
    public void testGetCategories_Preflight_VercelOrigin() throws Exception {
        mockMvc.perform(options("/api/categories")
                .header("Origin", "https://timeverse-xpo135qhj-time-verse1.vercel.app")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://timeverse-xpo135qhj-time-verse1.vercel.app"));
    }

    @Test
    public void testGetProducts_Cors_VercelOrigin() throws Exception {
        mockMvc.perform(get("/api/products")
                .header("Origin", "https://timeverse-xpo135qhj-time-verse1.vercel.app")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://timeverse-xpo135qhj-time-verse1.vercel.app"));
    }

    @Test
    public void testGetProductsFilter_Cors_VercelOrigin() throws Exception {
        mockMvc.perform(get("/api/products/filter?page=0&size=40")
                .header("Origin", "https://timeverse-xpo135qhj-time-verse1.vercel.app")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://timeverse-xpo135qhj-time-verse1.vercel.app"));
    }

    @Test
    public void testGetProductById_NotFound() throws Exception {
        mockMvc.perform(get("/api/products/999999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testLogin_ValidationFailure() throws Exception {
        String invalidLoginJson = "{\"email\":\"invalid-email\",\"password\":\"\"}";
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidLoginJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testRegister_ValidationFailure() throws Exception {
        String invalidRegisterJson = "{\"username\":\"\",\"email\":\"bademail\",\"password\":\"short\"}";
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRegisterJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testOrders_RequiresAuthorization() throws Exception {
        mockMvc.perform(get("/api/orders/all")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @org.springframework.transaction.annotation.Transactional
    public void testCreateProduct_Success() throws Exception {
        Long catId = categoryRepository.findAll().stream().findFirst()
                .map(com.timeverse.backend.entity.Category::getCategoryId).orElse(1L);

        String validProductJson = "{" +
                "\"name\":\"Integration Test Watch\"," +
                "\"description\":\"A watch created during integration testing.\"," +
                "\"price\":1500.00," +
                "\"stock\":10," +
                "\"categoryId\":" + catId + "," +
                "\"imageUrls\":[\"http://example.com/watch.jpg\"]" +
                "}";

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Integration Test Watch"))
                .andExpect(jsonPath("$.price").value(1500.00));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testCreateProduct_ValidationFailure() throws Exception {
        // Name is blank, price is negative, stock is negative
        String invalidProductJson = "{" +
                "\"name\":\"\"," +
                "\"description\":\"Invalid watch description.\"," +
                "\"price\":-100.00," +
                "\"stock\":-5," +
                "\"categoryId\":1" +
                "}";

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidProductJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateProduct_UnauthorizedForGuests() throws Exception {
        String validProductJson = "{" +
                "\"name\":\"Guest Watch\"," +
                "\"description\":\"A watch description.\"," +
                "\"price\":1200.00," +
                "\"stock\":10," +
                "\"categoryId\":1" +
                "}";

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validProductJson))
                .andExpect(status().isForbidden());
    }
}
