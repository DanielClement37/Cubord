package org.example.cubordbackend.service;

import org.example.cubordbackend.domain.Location;
import org.example.cubordbackend.domain.PantryItem;
import org.example.cubordbackend.domain.Product;
import org.example.cubordbackend.dto.CreatePantryItemRequest;
import org.example.cubordbackend.dto.PantryItemResponse;
import org.example.cubordbackend.repository.LocationRepository;
import org.example.cubordbackend.repository.PantryItemRepository;
import org.example.cubordbackend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PantryItemService {

    private final PantryItemRepository pantryItemRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    public PantryItemResponse createPantryItem(CreatePantryItemRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new RuntimeException("Location not found"));

        PantryItem newItem = PantryItem.builder()
                .product(product)
                .location(location)
                .expirationDate(request.getExpirationDate())
                .quantity(request.getQuantity())
                .unitOfMeasure(request.getUnitOfMeasure())
                .notes(request.getNotes())
                .build();

        PantryItem savedItem = pantryItemRepository.save(newItem);

        return PantryItemResponse.builder()
                .id(savedItem.getId())
                .productId(savedItem.getProduct().getId())
                .locationId(savedItem.getLocation().getId())
                .expirationDate(savedItem.getExpirationDate())
                .quantity(savedItem.getQuantity())
                .unitOfMeasure(savedItem.getUnitOfMeasure())
                .notes(savedItem.getNotes())
                .createdAt(savedItem.getCreatedAt())
                .updatedAt(savedItem.getUpdatedAt())
                .build();
    }
}
