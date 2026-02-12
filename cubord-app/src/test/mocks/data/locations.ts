import type { LocationResponse } from '@/types';

export const mockLocation: LocationResponse = {
    id: 'loc-001',
    name: 'Kitchen Fridge',
    description: 'Main refrigerator in the kitchen',
    householdId: 'hh-001',
    householdName: 'Doe Family',
    createdAt: '2025-11-02T09:00:00Z',
    updatedAt: '2025-11-02T09:00:00Z',
};

export const mockLocations: LocationResponse[] = [
    mockLocation,
    {
        id: 'loc-002',
        name: 'Pantry Shelf',
        description: 'Dry goods shelf in the hallway',
        householdId: 'hh-001',
        householdName: 'Doe Family',
        createdAt: '2025-11-03T10:00:00Z',
        updatedAt: '2025-11-03T10:00:00Z',
    },
];