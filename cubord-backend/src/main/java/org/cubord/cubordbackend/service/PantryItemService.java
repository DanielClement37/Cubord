package org.cubord.cubordbackend.service;

import org.cubord.cubordbackend.domain.Location;
import org.cubord.cubordbackend.domain.PantryItem;
import org.cubord.cubordbackend.domain.Product;
import org.cubord.cubordbackend.dto.CreatePantryItemRequest;
import org.cubord.cubordbackend.dto.PantryItemResponse;
import org.cubord.cubordbackend.repository.LocationRepository;
import org.cubord.cubordbackend.repository.PantryItemRepository;
import org.cubord.cubordbackend.repository.ProductRepository;
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
