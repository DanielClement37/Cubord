// src/hooks/queries/useProductByUpc.ts
import { useQuery } from '@tanstack/react-query';
import { getProductByUpc } from '@/api/products';
import type { ProductResponse } from '@/types';

export function useProductByUpc(upc: string) {
    return useQuery<ProductResponse>({
        queryKey: ['products', 'upc', upc],
        queryFn: () => getProductByUpc(upc),
        enabled: false, // manually triggered via refetch()
    });
}