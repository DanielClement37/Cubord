import type { HouseholdResponse } from '@/types';

export const mockHousehold: HouseholdResponse = {
    id: 'hh-001',
    name: 'Doe Family',
    createdAt: '2025-11-01T12:00:00Z',
    updatedAt: '2025-12-01T09:00:00Z',
};

export const mockHouseholds: HouseholdResponse[] = [
    mockHousehold,
    {
        id: 'hh-002',
        name: 'Beach House',
        createdAt: '2025-11-15T14:00:00Z',
        updatedAt: '2025-12-20T11:00:00Z',
    },
];