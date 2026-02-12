export interface LocationRequest {
    /** @maxLength 255 */
    name: string;
    /** @maxLength 500 */
    description?: string | null;
    householdId: string;
}

export interface LocationUpdateRequest {
    /** @maxLength 255 */
    name: string;
    /** @maxLength 500 */
    description?: string | null;
}

export interface LocationResponse {
    id: string;
    name: string;
    description: string | null;
    householdId: string;
    householdName: string | null;
    createdAt: string;
    updatedAt: string;
}