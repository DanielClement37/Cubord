// src/hooks/queries/usePantryItems.ts
import { useQuery } from '@tanstack/react-query';
import { getPantryItems, type PaginatedResponse } from '@/api/pantryItems';
import type { PantryItemResponse } from '@/types';

export function usePantryItems(householdId: string | undefined, page = 0, size = 20) {
    return useQuery<PaginatedResponse<PantryItemResponse>>({
        queryKey: ['pantry-items', householdId, { page, size }],
        queryFn: () => getPantryItems({ householdId: householdId!, page, size }),
        enabled: !!householdId,
    });
}