import { useMutation, useQueryClient } from '@tanstack/react-query';
import { patchPantryItem } from '@/api/pantryItems';
import type { PantryItemResponse, UpdatePantryItemRequest } from '@/types';
import Toast from 'react-native-toast-message';
import * as Haptics from 'expo-haptics';

interface UpdatePantryItemVars {
    id: string;
    data: Partial<UpdatePantryItemRequest>;
}

interface UseUpdatePantryItemOptions {
    householdId: string;
    /** Called after a successful mutation. */
    onSuccess?: (updated: PantryItemResponse) => void;
    /** If true, suppresses the default success toast. */
    silent?: boolean;
}

/**
 * Mutation hook for partially updating a pantry item (PATCH).
 *
 * Automatically invalidates pantry-related queries on success
 * and shows a toast unless `silent` is set.
 */
export function useUpdatePantryItem({
                                        householdId,
                                        onSuccess,
                                        silent = false,
                                    }: UseUpdatePantryItemOptions) {
    const queryClient = useQueryClient();

    return useMutation<PantryItemResponse, Error, UpdatePantryItemVars>({
        mutationFn: ({ id, data }) => patchPantryItem(id, data),
        onSuccess: async (updated) => {
            await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);

            await Promise.all([
                queryClient.invalidateQueries({ queryKey: ['pantry-items', householdId] }),
                queryClient.invalidateQueries({ queryKey: ['pantry-items', householdId, 'statistics'] }),
                queryClient.invalidateQueries({ queryKey: ['pantry-items', householdId, 'expiring'] }),
                queryClient.invalidateQueries({
                    queryKey: ['location-pantry-items', updated.location.id],
                }),
            ]);

            if (!silent) {
                Toast.show({
                    type: 'success',
                    text1: 'Item updated',
                    text2: updated.product.name,
                });
            }

            onSuccess?.(updated);
        },
        onError: (error) => {
            Toast.show({
                type: 'error',
                text1: 'Update failed',
                text2: error.message,
            });
        },
    });
}