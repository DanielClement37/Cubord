// src/hooks/queries/useLocations.ts
import { useQuery } from '@tanstack/react-query';
import { getLocations } from '@/api/locations';
import type { LocationResponse } from '@/types';

export function useLocations(householdId: string | undefined) {
    return useQuery<LocationResponse[]>({
        queryKey: ['locations', householdId],
        queryFn: () => getLocations(householdId!),
        enabled: !!householdId,
    });
}