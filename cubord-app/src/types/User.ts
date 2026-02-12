export enum UserRole {
    USER = "USER",
    ADMIN = "ADMIN",
}

export interface UserRequest {
    username: string;
    /** Must be a valid email */
    email: string;
    displayName?: string | null;
}

export interface UserUpdateRequest {
    /** @minLength 2 @maxLength 50 */
    displayName?: string | null;
    /** Must be a valid email */
    email?: string | null;
    username?: string | null;
}

export interface UserResponse {
    id: string;
    username: string;
    email: string;
    displayName: string;
    createdAt: string;
    updatedAt: string;
}