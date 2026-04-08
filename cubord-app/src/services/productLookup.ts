import { getProductByUpc } from '@/api/products';
import { ApiError } from '@/api/client';
import type { ProductResponse } from '@/types';

export type ProductLookupResult =
    | { status: 'found'; product: ProductResponse }
    | { status: 'not_found'; upc: string };

/**
 * Returns true when the backend gave us a real, identified product
 * rather than a placeholder stub.
 */
function isValidProduct(product: ProductResponse): boolean {
    const name = product.name?.trim().toLowerCase() ?? '';
    return (
        name.length > 0 &&
        name !== 'unknown product' &&
        name !== 'unknown'
    );
}

/**
 * Looks up a product by UPC barcode.
 * The backend handles: DB cache → OpenFoodFacts fallback → 404.
 * This is a read-only lookup — no pantry item is created yet.
 */
export async function lookupProductByUPC(upc: string): Promise<ProductLookupResult> {
    try {
        const product = await getProductByUpc(upc);

        // Backend may return 200 with a placeholder — treat as not found
        if (!isValidProduct(product)) {
            return { status: 'not_found', upc };
        }

        return { status: 'found', product };
    } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
            return { status: 'not_found', upc };
        }
        throw error;
    }
}