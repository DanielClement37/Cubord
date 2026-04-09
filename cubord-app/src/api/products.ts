import {apiClient, PageResponse} from './client';
import type { ProductRequest, ProductResponse } from '@/types';

/**
 * Looks up a product by its UPC barcode.
 */
export function getProductByUpc(upc: string): Promise<ProductResponse> {
    return apiClient<ProductResponse>(`/products/upc/${upc}`, 'GET');
}

/**
 * Creates a new product (may be auto-enriched from external APIs).
 */
export function createProduct(data: ProductRequest): Promise<ProductResponse> {
    return apiClient<ProductResponse>('/products', 'POST', data);
}


/**
 * Searches products by name, brand, or category.
 * Returns a paginated result from the backend.
 *
 * @param query  Search text (max 100 chars)
 * @param page   Zero-based page number (default 0)
 * @param size   Page size (default 20)
 */
export function searchProducts(
    query: string,
    page = 0,
    size = 20,
): Promise<PageResponse<ProductResponse>> {
    return apiClient<PageResponse<ProductResponse>>(
        `/products/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`,
        'GET',
    );
}