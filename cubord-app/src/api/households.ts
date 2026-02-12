import { apiClient } from './client';
import type { HouseholdRequest, HouseholdResponse } from '@/types';

/**
 * Retrieves all households the current user is a member of.
 */
export function getHouseholds(): Promise<HouseholdResponse[]> {
    return apiClient<HouseholdResponse[]>('/households', 'GET');
}

/**
 * Creates a new household with the current user as an owner.
 */
export function createHousehold(data: HouseholdRequest): Promise<HouseholdResponse> {
    return apiClient<HouseholdResponse>('/households', 'POST', data);
}
