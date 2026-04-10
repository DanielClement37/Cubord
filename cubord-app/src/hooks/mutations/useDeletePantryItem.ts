import { useMutation, useQueryClient } from '@tanstack/react-query';
import { deletePantryItem } from '@/api/pantryItems';
import Toast from 'react-native-toast-message';
import * as Haptics from 'expo-haptics';

interface UseDeletePantryItemOptions {
    householdId: string;
    /** Called after a successful deletion. */
    onSuccess?: () => void;
}

/**
 * Mutation hook for deleting a pantry item.
 *
 * Automatically invalidates pantry-related queries on success,
 * provides haptic feedback, and shows a toast.
 */
export function useDeletePantryItem({
                                        householdId,
                                        onSuccess,
                                    }: UseDeletePantryItemOptions) {
    const queryClient = useQueryClient();

    return useMutation<void, Error, string>({
        mutationFn: (id) => deletePantryItem(id),
        onSuccess: async () => {
            await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);

            await Promise.all([
                queryClient.invalidateQueries({ queryKey: ['pantry-items', householdId] }),
                queryClient.invalidateQueries({ queryKey: ['pantry-items', householdId, 'statistics'] }),
                queryClient.invalidateQueries({ queryKey: ['pantry-items', householdId, 'expiring'] }),
                queryClient.invalidateQueries({ queryKey: ['locations', householdId] }),
            ]);

            Toast.show({
                type: 'success',
                text1: 'Item removed',
                text2: 'Pantry item deleted successfully',
            });

            onSuccess?.();
        },
        onError: (error) => {
            Toast.show({
                type: 'error',
                text1: 'Delete failed',
                text2: error.message,
            });
        },
    });
}