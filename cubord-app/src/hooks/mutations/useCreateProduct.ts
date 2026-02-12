// src/hooks/mutations/useCreateProduct.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createProduct } from '@/api/products';
import type { ProductRequest, ProductResponse } from '@/types';

export function useCreateProduct() {
    const queryClient = useQueryClient();

    return useMutation<ProductResponse, Error, ProductRequest>({
        mutationFn: createProduct,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['products'] });
        },
    });
}