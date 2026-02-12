// src/hooks/mutations/useCreateHousehold.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createHousehold } from '@/api/households';
import type { HouseholdRequest, HouseholdResponse } from '@/types';
import Toast from 'react-native-toast-message';

export function useCreateHousehold() {
    const queryClient = useQueryClient();

    return useMutation<HouseholdResponse, Error, HouseholdRequest>({
        mutationFn: createHousehold,
        onSuccess: async (data) => {
            await queryClient.invalidateQueries({ queryKey: ['households'] });
            Toast.show({ type: 'success', text1: 'Household created', text2: data.name });
        },
    });
}