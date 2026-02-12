// src/hooks/mutations/useCreateLocation.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createLocation } from '@/api/locations';
import type { LocationRequest, LocationResponse } from '@/types';
import Toast from 'react-native-toast-message';

export function useCreateLocation() {
    const queryClient = useQueryClient();

    return useMutation<LocationResponse, Error, LocationRequest>({
        mutationFn: createLocation,
        onSuccess: async (data) => {
            await queryClient.invalidateQueries({ queryKey: ['locations', data.householdId] });
            Toast.show({ type: 'success', text1: 'Location created', text2: data.name });
        },
    });
}