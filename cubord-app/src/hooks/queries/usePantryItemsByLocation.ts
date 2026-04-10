import { useQuery } from '@tanstack/react-query';
import { getPantryItemsByLocation } from '@/api/pantryItems';
import type { PantryItemResponse } from '@/types';

/**
 * Fetches all pantry items belonging to a specific location.
 * Uses the `GET /locations/{locationId}/pantry-items` endpoint.
 */
export function usePantryItemsByLocation(locationId: string | undefined) {
    return useQuery<PantryItemResponse[]>({
        queryKey: ['location-pantry-items', locationId],
        queryFn: () => getPantryItemsByLocation(locationId!),
        enabled: !!locationId,
    });
}