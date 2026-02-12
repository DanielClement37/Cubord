
// src/test/mocks/handlers.ts
import { http, HttpResponse } from 'msw';
import type { ErrorResponse } from '@/types';
import {
    mockUser,
    mockHouseholds,
    mockHousehold,
    mockProduct,
    mockLocations,
    mockPantryItems,
    mockPantryItem,
    mockPantryStatistics,
} from './data';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Wildcard base so handlers work regardless of the API_BASE_URL env value. */
const api = (path: string) => `*/api${path}`;

function errorBody(code: string, message: string): ErrorResponse {
    return {
        error_code: code,
        message,
        timestamp: new Date().toISOString(),
        correlation_id: 'mock-correlation-id',
    };
}

// ---------------------------------------------------------------------------
// Success handlers
// ---------------------------------------------------------------------------

const successHandlers = [
    // ---- Users ----------------------------------------------------------
    http.get(api('/users/me'), () => {
        return HttpResponse.json(mockUser);
    }),

    // ---- Households -----------------------------------------------------
    http.get(api('/households'), () => {
        return HttpResponse.json(mockHouseholds);
    }),

    http.post(api('/households'), async ({ request }) => {
        const body = (await request.json()) as { name: string };
        return HttpResponse.json(
            {
                ...mockHousehold,
                id: 'hh-new',
                name: body.name,
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            },
            { status: 201 },
        );
    }),

    // ---- Locations ------------------------------------------------------
    http.get(api('/households/:householdId/locations'), () => {
        return HttpResponse.json(mockLocations);
    }),

    // ---- Products -------------------------------------------------------
    http.get(api('/products/upc/:upc'), () => {
        return HttpResponse.json(mockProduct);
    }),

    http.post(api('/products'), async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
            {
                ...mockProduct,
                id: 'prod-new',
                ...body,
                dataSource: 'MANUAL',
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            },
            { status: 201 },
        );
    }),

    // ---- Pantry Items ---------------------------------------------------
    http.get(api('/households/:householdId/pantry-items'), ({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get('page') ?? '0');
        const size = Number(url.searchParams.get('size') ?? '20');

        return HttpResponse.json({
            content: mockPantryItems,
            totalElements: mockPantryItems.length,
            totalPages: 1,
            number: page,
            size,
        });
    }),

    http.post(api('/pantry-items'), () => {
        return HttpResponse.json(mockPantryItem, { status: 201 });
    }),

    http.get(api('/households/:householdId/pantry-items/expiring'), () => {
        // Return the first item as "expiring soon"
        return HttpResponse.json([mockPantryItems[0]]);
    }),

    http.get(api('/households/:householdId/pantry-items/statistics'), () => {
        return HttpResponse.json(mockPantryStatistics);
    }),
];

// ---------------------------------------------------------------------------
// Error handlers — use with `server.use(...)` in individual tests
// ---------------------------------------------------------------------------

/** 404 when a UPC is not found in the product database. */
export const productNotFoundHandler = http.get(
    api('/products/upc/:upc'),
    () => {
        return HttpResponse.json(
            errorBody('PRODUCT_NOT_FOUND', 'No product found for the given UPC.'),
            { status: 404 },
        );
    },
);

/** 401 when the auth token is expired or missing. */
export const unauthorizedHandler = http.get(
    api('/users/me'),
    () => {
        return HttpResponse.json(
            errorBody('UNAUTHORIZED', 'Authentication credentials are missing or expired.'),
            { status: 401 },
        );
    },
);

// ---------------------------------------------------------------------------
// Exported default handler array
// ---------------------------------------------------------------------------

export const handlers = [...successHandlers];