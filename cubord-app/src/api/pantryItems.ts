import { apiClient } from './client';
import type { CreatePantryItemRequest, PantryItemResponse, PantryStatistics } from '@/types';

export interface GetPantryItemsParams {
    householdId: string;
    page?: number;
    size?: number;
}

export interface PaginatedResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
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
): Promise<PaginatedResponse<PantryItemResponse>> {
    const { householdId, page = 0, size = 20 } = params;
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    return apiClient<PaginatedResponse<PantryItemResponse>>(
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