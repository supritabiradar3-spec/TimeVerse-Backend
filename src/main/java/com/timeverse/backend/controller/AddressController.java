package com.timeverse.backend.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.timeverse.backend.entity.Address;
import com.timeverse.backend.repository.AddressRepository;
import com.timeverse.backend.service.JwtService;

@RestController
@RequestMapping("/api/addresses")
@CrossOrigin(origins = "*")
public class AddressController {

    private final AddressRepository addressRepository;
    private final JwtService jwtService;

    public AddressController(AddressRepository addressRepository, JwtService jwtService) {
        this.addressRepository = addressRepository;
        this.jwtService = jwtService;
    }

    private Long getUserIdFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Authorization token is missing");
        }
        String token = authHeader.substring(7);
        return jwtService.extractUserId(token);
    }

    @GetMapping
    public ResponseEntity<List<Address>> getAddresses(@RequestHeader("Authorization") String authHeader) {
        Long userId = getUserIdFromHeader(authHeader);
        List<Address> rawList = addressRepository.findByUserId(userId);
        List<Address> uniqueList = new java.util.ArrayList<>();
        for (Address addr : rawList) {
            if (addr == null) continue;
            int existingIndex = -1;
            for (int i = 0; i < uniqueList.size(); i++) {
                if (isSameAddress(uniqueList.get(i), addr)) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex == -1) {
                uniqueList.add(addr);
            } else if (addr.isDefault() && !uniqueList.get(existingIndex).isDefault()) {
                uniqueList.set(existingIndex, addr);
            }
        }
        return ResponseEntity.ok(uniqueList);
    }

    @PostMapping
    public ResponseEntity<Address> addAddress(
            @RequestBody Address address,
            @RequestHeader("Authorization") String authHeader) {
        
        Long userId = getUserIdFromHeader(authHeader);
        address.setUserId(userId);

        List<Address> existing = addressRepository.findByUserId(userId);

        // Check if an identical address already exists for this user
        for (Address ex : existing) {
            if (isSameAddress(ex, address)) {
                if (address.isDefault() && !ex.isDefault()) {
                    clearOtherDefaults(userId);
                    ex.setDefault(true);
                    return ResponseEntity.ok(addressRepository.save(ex));
                }
                return ResponseEntity.ok(ex);
            }
        }

        if (existing.isEmpty()) {
            address.setDefault(true);
        } else if (address.isDefault()) {
            clearOtherDefaults(userId);
        }

        return ResponseEntity.ok(addressRepository.save(address));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<Address> updateAddress(
            @PathVariable Long addressId,
            @RequestBody Address addressDetails,
            @RequestHeader("Authorization") String authHeader) {
        
        Long userId = getUserIdFromHeader(authHeader);
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to address");
        }

        address.setFullName(addressDetails.getFullName());
        address.setPhone(addressDetails.getPhone());
        address.setStreet(addressDetails.getStreet());
        address.setCity(addressDetails.getCity());
        address.setState(addressDetails.getState());
        address.setZipCode(addressDetails.getZipCode());
        address.setCountry(addressDetails.getCountry());

        if (addressDetails.isDefault() && !address.isDefault()) {
            clearOtherDefaults(userId);
            address.setDefault(true);
        } else {
            address.setDefault(addressDetails.isDefault());
        }

        return ResponseEntity.ok(addressRepository.save(address));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @PathVariable Long addressId,
            @RequestHeader("Authorization") String authHeader) {
        
        Long userId = getUserIdFromHeader(authHeader);
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to address");
        }

        addressRepository.delete(address);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{addressId}/default")
    public ResponseEntity<Address> setDefault(
            @PathVariable Long addressId,
            @RequestHeader("Authorization") String authHeader) {
        
        Long userId = getUserIdFromHeader(authHeader);
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (!address.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to address");
        }

        clearOtherDefaults(userId);
        address.setDefault(true);
        return ResponseEntity.ok(addressRepository.save(address));
    }

    private void clearOtherDefaults(Long userId) {
        List<Address> addresses = addressRepository.findByUserId(userId);
        for (Address addr : addresses) {
            if (addr.isDefault()) {
                addr.setDefault(false);
                addressRepository.save(addr);
            }
        }
    }

    private boolean isSameAddress(Address a1, Address a2) {
        if (a1 == null || a2 == null) return false;
        return isSame(a1.getFullName(), a2.getFullName())
                && isSamePhone(a1.getPhone(), a2.getPhone())
                && isSame(a1.getStreet(), a2.getStreet())
                && isSame(a1.getCity(), a2.getCity())
                && isSame(a1.getState(), a2.getState())
                && isSame(a1.getZipCode(), a2.getZipCode())
                && isSameCountry(a1.getCountry(), a2.getCountry());
    }

    private boolean isSame(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.trim().equalsIgnoreCase(s2.trim());
    }

    private boolean isSamePhone(String p1, String p2) {
        if (p1 == null && p2 == null) return true;
        if (p1 == null || p2 == null) return false;
        String d1 = p1.replaceAll("[^0-9]", "");
        String d2 = p2.replaceAll("[^0-9]", "");
        return d1.equals(d2);
    }

    private boolean isSameCountry(String c1, String c2) {
        String norm1 = (c1 == null || c1.trim().isEmpty()) ? "india" : c1.trim().toLowerCase();
        String norm2 = (c2 == null || c2.trim().isEmpty()) ? "india" : c2.trim().toLowerCase();
        return norm1.equals(norm2);
    }
}
