
package org.cubord.cubordbackend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "household_invitations")
@Getter
@Setter
@ToString(exclude = {"invitedUser", "household", "invitedBy"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class HouseholdInvitation {
    @Id
    private UUID id;

    /**
     * The user being invited. This is nullable for invitations sent to email addresses
     * where the user hasn't created an account yet. When the user signs up with that email,
     * this field gets populated and invitedEmail is cleared.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_user_id")
    private User invitedUser;

    /**
     * Email address for the invitation when the user doesn't have an account yet.
     * This is mutually exclusive with invitedUser - when a user signs up with this email,
     * invitedUser gets set and this field is cleared.
     */
    @Column(name = "invited_email")
    private String invitedEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id")
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private User invitedBy;

    @Enumerated(EnumType.STRING)
    private HouseholdRole proposedRole;

    @Enumerated(EnumType.STRING)
    private InvitationStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.expiresAt == null) {
            this.expiresAt = LocalDateTime.now().plusDays(7); // Default 7 days expiry
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Returns the effective email for this invitation.
     * Either from the linked user or from the invitedEmail field.
     */
    public String getEffectiveEmail() {
        if (invitedUser != null) {
            return invitedUser.getEmail();
        }
        return invitedEmail;
    }

    /**
     * Returns whether this invitation is for an email-only recipient (no user account yet).
     */
    public boolean isEmailOnlyInvitation() {
        return invitedUser == null && invitedEmail != null;
    }
}