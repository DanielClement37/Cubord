// src/test/factories.ts
import type {
    HouseholdResponse,
    LocationResponse,
    PantryItemResponse,
    PantryStatistics,
    ProductResponse,
} from '@/types';
import { ProductDataSource } from '@/types';

let idCounter = 0;
const nextId = (prefix: string) => `${prefix}-${++idCounter}`;

// Reset between tests if needed
export function resetFactoryIds() {
    idCounter = 0;
}

const ISO_NOW = '2026-03-25T12:00:00Z';

// ── Household ─────────────────────────────────────────
export function buildHousehold(overrides: Partial<HouseholdResponse> = {}): HouseholdResponse {
    return {
        id: nextId('hh'),
        name: 'Test Household',
        createdAt: ISO_NOW,
        updatedAt: ISO_NOW,
        ...overrides,
    };
}

// ── Product ───────────────────────────────────────────
export function buildProduct(overrides: Partial<ProductResponse> = {}): ProductResponse {
    return {
        id: nextId('prod'),
        upc: '000000000000',
        name: 'Test Product',
        brand: 'Test Brand',
        category: 'General',
        defaultExpirationDays: 14,
        dataSource: ProductDataSource.MANUAL,
        createdAt: ISO_NOW,
        updatedAt: ISO_NOW,
        ...overrides,
    };
}

// ── Location ──────────────────────────────────────────
export function buildLocation(overrides: Partial<LocationResponse> = {}): LocationResponse {
    return {
        id: nextId('loc'),
        name: 'Test Location',
        description: null,
        householdId: 'hh-001',
        householdName: 'Test Household',
        createdAt: ISO_NOW,
        updatedAt: ISO_NOW,
        ...overrides,
    };
}

// ── Pantry Item ───────────────────────────────────────
export function buildPantryItem(overrides: Partial<PantryItemResponse> = {}): PantryItemResponse {
    return {
        id: nextId('pi'),
        product: buildProduct(),
        location: buildLocation(),
        expirationDate: '2026-04-01',
        quantity: 1,
        unitOfMeasure: null,
        notes: null,
        createdAt: ISO_NOW,
        updatedAt: ISO_NOW,
        ...overrides,
    };
}

// ── Pantry Statistics ─────────────────────────────────
export function buildPantryStatistics(overrides: Partial<PantryStatistics> = {}): PantryStatistics {
    return {
        totalItems: 0,
        distinctProducts: 0,
        lowStockCount: 0,
        expiringCount: 0,
        noExpirationDateCount: 0,
        ...overrides,
    };
}