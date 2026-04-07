import { getProductByUpc } from '@/api/products';
import { ApiError } from '@/api/client';
import type { ProductResponse } from '@/types';

export type ProductLookupResult =
    | { status: 'found'; product: ProductResponse }
    | { status: 'not_found'; upc: string };

/**
 * Looks up a product by UPC barcode.
 * The backend handles: DB cache → OpenFoodFacts fallback → 404.
 * This is a read-only lookup — no pantry item is created yet.
 */
export async function lookupProductByUPC(upc: string): Promise<ProductLookupResult> {
    try {
        const product = await getProductByUpc(upc);
        return { status: 'found', product };
    } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
            return { status: 'not_found', upc };
        }
        throw error;
    }
}