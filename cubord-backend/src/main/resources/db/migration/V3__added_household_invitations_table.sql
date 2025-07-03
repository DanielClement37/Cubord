CREATE TABLE household_invitations
(
    id                 UUID NOT NULL,
    invited_user_id    UUID,
    household_id       UUID,
    invited_by_user_id UUID,
    proposed_role      VARCHAR(255),
    status             VARCHAR(255),
    created_at         TIMESTAMP WITHOUT TIME ZONE,
    updated_at         TIMESTAMP WITHOUT TIME ZONE,
    expires_at         TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_household_invitations PRIMARY KEY (id)
);

ALTER TABLE household_invitations
    ADD CONSTRAINT FK_HOUSEHOLD_INVITATIONS_ON_HOUSEHOLD FOREIGN KEY (household_id) REFERENCES households (id);

ALTER TABLE household_invitations
    ADD CONSTRAINT FK_HOUSEHOLD_INVITATIONS_ON_INVITED_BY_USER FOREIGN KEY (invited_by_user_id) REFERENCES users (id);

ALTER TABLE household_invitations
    ADD CONSTRAINT FK_HOUSEHOLD_INVITATIONS_ON_INVITED_USER FOREIGN KEY (invited_user_id) REFERENCES users (id);