// src/hooks/queries/usePantryStatistics.ts
import { useQuery } from '@tanstack/react-query';
import { getPantryStatistics } from '@/api/pantryItems';
import type { PantryStatistics } from '@/types';

export function usePantryStatistics(householdId: string | undefined) {
    return useQuery<PantryStatistics>({
        queryKey: ['pantry-items', householdId, 'statistics'],
        queryFn: () => getPantryStatistics(householdId!),
        enabled: !!householdId,
    });
}