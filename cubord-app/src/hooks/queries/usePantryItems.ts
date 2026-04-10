// src/hooks/queries/usePantryItems.ts
import { useQuery } from '@tanstack/react-query';
import { getPantryItems } from '@/api/pantryItems';
import { type PageResponse } from '@/api/client';
import type { PantryItemResponse } from '@/types';

export function usePantryItems(householdId: string | undefined, page = 0, size = 20) {
    return useQuery<PageResponse<PantryItemResponse>>({
        queryKey: ['pantry-items', householdId, { page, size }],
        queryFn: () => getPantryItems({ householdId: householdId!, page, size }),
        enabled: !!householdId,
    });
}