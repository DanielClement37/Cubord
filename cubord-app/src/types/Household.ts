export enum HouseholdRole {
    OWNER = "OWNER",
    ADMIN = "ADMIN",
    MEMBER = "MEMBER",
}

export enum InvitationStatus {
    PENDING = "PENDING",
    ACCEPTED = "ACCEPTED",
    DECLINED = "DECLINED",
    EXPIRED = "EXPIRED",
    CANCELLED = "CANCELLED",
}

export interface HouseholdRequest {
    /** @minLength 2 @maxLength 50 */
    name: string;
}

export interface HouseholdMemberRequest {
    userId: string;
    role: HouseholdRole;
}

export interface HouseholdMemberRoleUpdateRequest {
    role: HouseholdRole;
}

export interface HouseholdInvitationRequest {
    /** Must be a valid email */
    invitedUserEmail?: string | null;
    /** Optional: if inviting by user ID instead of email */
    invitedUserId?: string | null;
    proposedRole: HouseholdRole;
    /** Optional: custom expiry (ISO 8601 datetime) */
    expiresAt?: string | null;
}

export interface HouseholdInvitationUpdateRequest {
    proposedRole?: HouseholdRole | null;
    /** ISO 8601 datetime */
    expiresAt?: string | null;
}

export interface ResendInvitationRequest {
    /** Optional: new expiry date (ISO 8601 datetime) */
    expiresAt?: string | null;
}

export interface HouseholdResponse {
    id: string;
    name: string;
    createdAt: string;
    updatedAt: string;
}

export interface HouseholdMemberResponse {
    id: string;
    userId: string;
    username: string;
    householdId: string;
    householdName: string;
    role: HouseholdRole;
    createdAt: string;
}

export interface HouseholdInvitationResponse {
    id: string;
    invitedUserId: string | null;
    invitedUserEmail: string | null;
    invitedUserName: string | null;
    householdId: string;
    householdName: string;
    invitedByUserId: string;
    invitedByUserName: string;
    proposedRole: HouseholdRole;
    status: InvitationStatus;
    createdAt: string;
    expiresAt: string;
}
