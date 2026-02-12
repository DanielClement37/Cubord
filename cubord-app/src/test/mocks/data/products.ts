import { ProductDataSource } from '@/types';
import type { ProductResponse } from '@/types';

export const mockProduct: ProductResponse = {
    id: 'prod-001',
    upc: '012345678901',
    name: 'Organic Whole Milk',
    brand: 'Happy Farms',
    category: 'Dairy',
    defaultExpirationDays: 14,
    dataSource: ProductDataSource.OPEN_FOOD_FACTS,
    createdAt: '2025-10-01T08:00:00Z',
    updatedAt: '2025-10-01T08:00:00Z',
};

export const mockProductManual: ProductResponse = {
    id: 'prod-002',
    upc: null,
    name: 'Homemade Salsa',
    brand: null,
    category: 'Condiments',
    defaultExpirationDays: 7,
    dataSource: ProductDataSource.MANUAL,
    createdAt: '2025-10-05T10:00:00Z',
    updatedAt: '2025-10-05T10:00:00Z',
};