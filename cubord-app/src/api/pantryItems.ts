import {apiClient, PageResponse} from './client';
import type {CreatePantryItemRequest, PantryItemResponse, PantryStatistics, UpdatePantryItemRequest} from '@/types';

export interface GetPantryItemsParams {
    householdId: string;
    page?: number;
    size?: number;
}



export interface GetExpiringItemsParams {
    householdId: string;
    /** ISO date (YYYY-MM-DD). Defaults to today on the server. */
    startDate?: string;
    /** ISO date (YYYY-MM-DD). Defaults to 7 days from today on the server. */
    endDate?: string;
}

/**
 * Retrieves paginated pantry items for a household.
 */
export function getPantryItems(
    params: GetPantryItemsParams,
): Promise<PageResponse<PantryItemResponse>> {
    const { householdId, page = 0, size = 20 } = params;
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    return apiClient<PageResponse<PantryItemResponse>>(
        `/households/${householdId}/pantry-items?${query}`,
        'GET',
    );
}

/**
 * Creates a new pantry item.
 */
export function createPantryItem(data: CreatePantryItemRequest): Promise<PantryItemResponse> {
    return apiClient<PantryItemResponse>('/pantry-items', 'POST', data);
}

/**
 * Retrieves a single pantry item by its ID.
 */
export function getPantryItem(id: string): Promise<PantryItemResponse> {
    return apiClient<PantryItemResponse>(`/pantry-items/${id}`, 'GET');
}

/**
 * Fully updates a pantry item (PUT).
 */
export function updatePantryItem(
    id: string,
    data: UpdatePantryItemRequest,
): Promise<PantryItemResponse> {
    return apiClient<PantryItemResponse>(`/pantry-items/${id}`, 'PUT', data);
}

/**
 * Partially updates a pantry item (PATCH).
 * Only the provided fields are updated on the server.
 */
export function patchPantryItem(
    id: string,
    data: Partial<UpdatePantryItemRequest>,
): Promise<PantryItemResponse> {
    return apiClient<PantryItemResponse>(`/pantry-items/${id}`, 'PATCH', data);
}

/**
 * Deletes a pantry item.
 */
export function deletePantryItem(id: string): Promise<void> {
    return apiClient<void>(`/pantry-items/${id}`, 'DELETE');
}

/**
 * Retrieves all pantry items for a specific location.
 */
export function getPantryItemsByLocation(
    locationId: string,
): Promise<PantryItemResponse[]> {
    return apiClient<PantryItemResponse[]>(
        `/locations/${locationId}/pantry-items`,
        'GET',
    );
}

/**
 * Searches pantry items by query string within a household.
 */
export function searchPantryItems(
    householdId: string,
    query: string,
): Promise<PantryItemResponse[]> {
    return apiClient<PantryItemResponse[]>(
        `/households/${householdId}/pantry-items/search?query=${encodeURIComponent(query)}`,
        'GET',
    );
}

/**
 * Retrieves low-stock pantry items for a household.
 */
export function getLowStockItems(
    householdId: string,
    threshold = 5,
): Promise<PantryItemResponse[]> {
    return apiClient<PantryItemResponse[]>(
        `/households/${householdId}/pantry-items/low-stock?threshold=${threshold}`,
        'GET',
    );
}

/**
 * Retrieves pantry items expiring within a date range for a household.
 */
export function getExpiringItems(params: GetExpiringItemsParams): Promise<PantryItemResponse[]> {
    const { householdId, startDate, endDate } = params;
    const query = new URLSearchParams();
    if (startDate) query.set('startDate', startDate);
    if (endDate) query.set('endDate', endDate);
    const qs = query.toString();
    return apiClient<PantryItemResponse[]>(
        `/households/${householdId}/pantry-items/expiring${qs ? `?${qs}` : ''}`,
        'GET',
    );
}

/**
 * Retrieves pantry statistics for a household (total items, expiring soon, etc.).
 */
export function getPantryStatistics(householdId: string): Promise<PantryStatistics> {
    return apiClient<PantryStatistics>(
        `/households/${householdId}/pantry-items/statistics`,
        'GET',
    );
}