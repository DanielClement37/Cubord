-- Add the invited_email column for invitations to users who don't have accounts yet
ALTER TABLE household_invitations
    ADD COLUMN invited_email VARCHAR(255);

-- Create an index for efficient email lookups
CREATE INDEX idx_household_invitations_invited_email 
    ON household_invitations(invited_email) 
    WHERE invited_email IS NOT NULL;

-- Add constraint: either invited_user_id OR invited_email must be set (not both null)
ALTER TABLE household_invitations
    ADD CONSTRAINT chk_invited_user_or_email 
    CHECK (invited_user_id IS NOT NULL OR invited_email IS NOT NULL);
