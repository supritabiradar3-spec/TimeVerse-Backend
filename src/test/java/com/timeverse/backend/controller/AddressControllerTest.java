package com.timeverse.backend.controller;

import com.timeverse.backend.entity.Address;
import com.timeverse.backend.repository.AddressRepository;
import com.timeverse.backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressControllerTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AddressController addressController;

    private final String authHeader = "Bearer test-jwt-token";
    private final Long userId = 44L;

    @BeforeEach
    void setUp() {
        lenient().when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);
    }

    @Test
    void testGetAddresses_DeduplicatesIdenticalRecords() {
        // Create 8 identical address records as reported in the duplication bug
        List<Address> duplicateRecords = new ArrayList<>();
        for (long i = 1; i <= 8; i++) {
            duplicateRecords.add(Address.builder()
                    .addressId(i)
                    .userId(userId)
                    .fullName("Seema")
                    .phone("9420569826")
                    .street("BTM 2nd stage")
                    .city("Bangalore")
                    .state("Karnataka")
                    .zipCode("590014")
                    .country("India")
                    .isDefault(i == 3) // 3rd record is marked default
                    .build());
        }

        when(addressRepository.findByUserId(userId)).thenReturn(duplicateRecords);

        ResponseEntity<List<Address>> response = addressController.getAddresses(authHeader);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        List<Address> result = response.getBody();
        assertNotNull(result);
        assertEquals(1, result.size(), "Duplicate addresses must be consolidated into exactly 1 address");
        assertEquals("Seema", result.get(0).getFullName());
        assertEquals("BTM 2nd stage", result.get(0).getStreet());
        assertEquals("590014", result.get(0).getZipCode());
        assertTrue(result.get(0).isDefault(), "Default address status must be preserved");
    }

    @Test
    void testGetAddresses_PreservesDistinctAddresses() {
        Address addr1 = Address.builder()
                .addressId(1L)
                .userId(userId)
                .fullName("Seema")
                .phone("9420569826")
                .street("BTM 2nd stage")
                .city("Bangalore")
                .state("Karnataka")
                .zipCode("590014")
                .country("India")
                .isDefault(true)
                .build();

        Address addr2 = Address.builder()
                .addressId(2L)
                .userId(userId)
                .fullName("Seema Work")
                .phone("9420569826")
                .street("Tech Park, Outer Ring Road")
                .city("Bangalore")
                .state("Karnataka")
                .zipCode("560103")
                .country("India")
                .isDefault(false)
                .build();

        when(addressRepository.findByUserId(userId)).thenReturn(Arrays.asList(addr1, addr2));

        ResponseEntity<List<Address>> response = addressController.getAddresses(authHeader);

        assertNotNull(response);
        List<Address> result = response.getBody();
        assertNotNull(result);
        assertEquals(2, result.size(), "Distinct addresses belonging to the same user must NOT be merged");
    }

    @Test
    void testAddAddress_DoesNotCreateDuplicate() {
        Address existingAddr = Address.builder()
                .addressId(1L)
                .userId(userId)
                .fullName("Seema")
                .phone("9420569826")
                .street("BTM 2nd stage")
                .city("Bangalore")
                .state("Karnataka")
                .zipCode("590014")
                .country("India")
                .isDefault(true)
                .build();

        Address newAddr = Address.builder()
                .fullName("Seema")
                .phone("9420569826")
                .street("BTM 2nd stage")
                .city("Bangalore")
                .state("Karnataka")
                .zipCode("590014")
                .country("India")
                .isDefault(false)
                .build();

        when(addressRepository.findByUserId(userId)).thenReturn(List.of(existingAddr));

        ResponseEntity<Address> response = addressController.addAddress(newAddr, authHeader);

        assertNotNull(response);
        assertEquals(1L, response.getBody().getAddressId());
        verify(addressRepository, never()).save(newAddr);
    }

    @Test
    void testDeleteAddress_Success() {
        Address existingAddr = Address.builder()
                .addressId(10L)
                .userId(userId)
                .fullName("Seema")
                .build();

        when(addressRepository.findById(10L)).thenReturn(Optional.of(existingAddr));

        ResponseEntity<Void> response = addressController.deleteAddress(10L, authHeader);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        verify(addressRepository, times(1)).delete(existingAddr);
    }
}
