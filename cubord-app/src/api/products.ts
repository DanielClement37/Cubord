import { apiClient } from './client';
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