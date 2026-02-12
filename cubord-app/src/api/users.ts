import { apiClient } from './client';
import type { UserResponse } from '@/types';

/**
 * Retrieves the currently authenticated user's profile.
 */
export function getCurrentUser(): Promise<UserResponse> {
    return apiClient<UserResponse>('/users/me', 'GET');
}