package org.cubord.cubordbackend.controller;

import lombok.RequiredArgsConstructor;
import org.cubord.cubordbackend.dto.CreatePantryItemRequest;
import org.cubord.cubordbackend.dto.PantryItemResponse;
import org.cubord.cubordbackend.dto.UpdatePantryItemRequest;
import org.cubord.cubordbackend.service.PantryItemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/pantry-items")
@RequiredArgsConstructor
public class PantryItemController {

}