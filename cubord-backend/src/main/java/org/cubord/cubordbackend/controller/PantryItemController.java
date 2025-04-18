package org.cubord.cubordbackend.controller;

import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.CreatePantryItemRequest;
import org.cubord.cubordbackend.dto.PantryItemResponse;
import org.cubord.cubordbackend.service.PantryItemService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pantry-items")
@RequiredArgsConstructor
public class PantryItemController {

    private final PantryItemService pantryItemService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PantryItemResponse createPantryItem(@RequestBody CreatePantryItemRequest request) {
        return pantryItemService.createPantryItem(request);
    }

    // Additional endpoints here:
    // - @GetMapping("/{id}")
    // - @PutMapping("/{id}")
    // - @DeleteMapping("/{id}")
    // etc.
}