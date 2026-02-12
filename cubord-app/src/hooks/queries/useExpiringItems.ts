// src/hooks/queries/useExpiringItems.ts
import { useQuery } from '@tanstack/react-query';
import { getExpiringItems, type GetExpiringItemsParams } from '@/api/pantryItems';
import type { PantryItemResponse } from '@/types';

export function useExpiringItems(
    householdId: string | undefined,
    dateRange?: Pick<GetExpiringItemsParams, 'startDate' | 'endDate'>,
) {
    return useQuery<PantryItemResponse[]>({
        queryKey: ['pantry-items', householdId, 'expiring'],
        queryFn: () =>
            getExpiringItems({
                householdId: householdId!,
                ...dateRange,
            }),
        enabled: !!householdId,
    });
}