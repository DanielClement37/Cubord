import { apiClient } from './client';
import type { LocationResponse } from '@/types';

/**
 * Retrieves all storage locations for a household.
 */
export function getLocations(householdId: string): Promise<LocationResponse[]> {
    return apiClient<LocationResponse[]>(
        `/households/${householdId}/locations`,
        'GET',
    );
}