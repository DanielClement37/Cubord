import type { PantryItemResponse } from '@/types';
import { mockProduct, mockProductManual } from './products';
import { mockLocation, mockLocations } from './locations';

export const mockPantryItem: PantryItemResponse = {
    id: 'pi-001',
    product: mockProduct,
    location: mockLocation,
    expirationDate: '2026-02-20',
    quantity: 1,
    unitOfMeasure: 'gallon',
    notes: null,
    createdAt: '2026-02-01T12:00:00Z',
    updatedAt: '2026-02-01T12:00:00Z',
};

export const mockPantryItems: PantryItemResponse[] = [
    mockPantryItem,
    {
        id: 'pi-002',
        product: mockProductManual,
        location: mockLocations[1],
        expirationDate: '2026-02-14',
        quantity: 2,
        unitOfMeasure: 'jar',
        notes: 'Spicy batch',
        createdAt: '2026-02-05T14:00:00Z',
        updatedAt: '2026-02-05T14:00:00Z',
    },
];

export const mockPantryStatistics = {
    totalItems: 12,
    expiringSoon: 3,
    expiredCount: 1,
    locationBreakdown: {
        'loc-001': 8,
        'loc-002': 4,
    },
};