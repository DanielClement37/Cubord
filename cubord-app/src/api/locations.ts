import { apiClient } from './client';
import type {LocationRequest, LocationResponse} from '@/types';

/**
 * Retrieves all storage locations for a household.
 */
export function getLocations(householdId: string): Promise<LocationResponse[]> {
    return apiClient<LocationResponse[]>(
        `/households/${householdId}/locations`,
        'GET',
    );
}

/**
 * Creates a new storage location within a household.
 */
export function createLocation(data: LocationRequest): Promise<LocationResponse> {
    return apiClient<LocationResponse>(
        `/households/${data.householdId}/locations`,
        'POST',
        data,
    );
}