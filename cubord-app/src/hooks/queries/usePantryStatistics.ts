// src/hooks/queries/usePantryStatistics.ts
import { useQuery } from '@tanstack/react-query';
import { getPantryStatistics } from '@/api/pantryItems';

export function usePantryStatistics(householdId: string | undefined) {
    return useQuery<Record<string, unknown>>({
        queryKey: ['pantry-items', householdId, 'statistics'],
        queryFn: () => getPantryStatistics(householdId!),
        enabled: !!householdId,
    });
}