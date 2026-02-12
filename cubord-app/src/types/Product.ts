export enum ProductDataSource {
    OPEN_FOOD_FACTS = "OPEN_FOOD_FACTS",
    MANUAL = "MANUAL",
    HYBRID = "HYBRID",
}

export interface ProductRequest {
    /** Digits only, 8–14 characters */
    upc: string;
    name: string;
    brand?: string | null;
    category?: string | null;
    /** Must be positive */
    defaultExpirationDays?: number | null;
}

export interface ProductUpdateRequest {
    name: string;
    brand?: string | null;
    category?: string | null;
    /** Must be positive */
    defaultExpirationDays?: number | null;
}

export interface ProductResponse {
    id: string;
    upc: string | null;
    name: string;
    brand: string | null;
    category: string | null;
    defaultExpirationDays: number | null;
    dataSource: ProductDataSource;
    createdAt: string;
    updatedAt: string;
}