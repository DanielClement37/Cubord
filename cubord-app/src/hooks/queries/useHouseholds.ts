// src/hooks/queries/useHouseholds.ts
import { useQuery } from '@tanstack/react-query';
import { getHouseholds } from '@/api/households';
import type { HouseholdResponse } from '@/types';

export function useHouseholds() {
    return useQuery<HouseholdResponse[]>({
        queryKey: ['households'],
        queryFn: getHouseholds,
    });
}