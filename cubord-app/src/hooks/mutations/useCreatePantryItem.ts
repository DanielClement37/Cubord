// src/hooks/mutations/useCreatePantryItem.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createPantryItem } from '@/api/pantryItems';
import type { CreatePantryItemRequest, PantryItemResponse } from '@/types';
import Toast from 'react-native-toast-message';

interface UseCreatePantryItemOptions {
    householdId: string;
    onSuccess?: () => void;
}

export function useCreatePantryItem({ householdId, onSuccess }: UseCreatePantryItemOptions) {
    const queryClient = useQueryClient();

    return useMutation<PantryItemResponse, Error, CreatePantryItemRequest>({
        mutationFn: createPantryItem,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['pantry-items', householdId] });
            queryClient.invalidateQueries({ queryKey: ['pantry-items', householdId, 'statistics'] });
            queryClient.invalidateQueries({ queryKey: ['pantry-items', householdId, 'expiring'] });

            Toast.show({ type: 'success', text1: 'Item added', text2: 'Pantry item saved successfully' });
            onSuccess?.();
        },
    });
}