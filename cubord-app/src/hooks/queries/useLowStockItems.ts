import { useQuery } from '@tanstack/react-query';
import { getLowStockItems } from '@/api/pantryItems';
import type { PantryItemResponse } from '@/types';

/**
 * Fetches pantry items below a stock threshold for a household.
 */
export function useLowStockItems(
    householdId: string | undefined,
    threshold = 5,
) {
    return useQuery<PantryItemResponse[]>({
        queryKey: ['pantry-items', householdId, 'low-stock', threshold],
        queryFn: () => getLowStockItems(householdId!, threshold),
        enabled: !!householdId,
    });
}