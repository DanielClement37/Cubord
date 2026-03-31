import {ProductResponse} from "./Product";
import {LocationResponse} from "./Location";

export interface CreatePantryItemRequest {
    productId: string;
    locationId: string;
    /** ISO date string (YYYY-MM-DD) */
    expirationDate?: string | null;
    /** ISO date string (YYYY-MM-DD) */
    purchaseDate?: string | null;
    /** @minimum 0 */
    quantity?: number | null;
    unitOfMeasure?: string | null;
    notes?: string | null;
}

export interface UpdatePantryItemRequest {
    locationId?: string | null;
    /** @minimum 0 */
    quantity?: number | null;
    unitOfMeasure?: string | null;
    /** ISO date string (YYYY-MM-DD) */
    expirationDate?: string | null;
    notes?: string | null;
}

export interface PantryItemResponse {
    id: string;
    product: ProductResponse;
    location: LocationResponse;
    /** ISO date (YYYY-MM-DD) */
    expirationDate: string | null;
    quantity: number | null;
    unitOfMeasure: string | null;
    notes: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface PantryStatistics {
    totalItems: number;
    distinctProducts: number;
    lowStockCount: number;
    expiringCount: number;
    noExpirationDateCount: number;
}