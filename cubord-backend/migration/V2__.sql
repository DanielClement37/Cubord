CREATE TABLE household_members
(
    id           UUID NOT NULL,
    user_id      UUID,
    household_id UUID,
    role         VARCHAR(255),
    created_at   TIMESTAMP WITHOUT TIME ZONE,
    updated_at   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_household_members PRIMARY KEY (id)
);

CREATE TABLE users
(
    id           UUID NOT NULL,
    email        VARCHAR(255),
    display_name VARCHAR(255),
    created_at   TIMESTAMP WITHOUT TIME ZONE,
    updated_at   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

ALTER TABLE locations
    ADD description VARCHAR(255);

ALTER TABLE locations
    ADD household_id UUID;

ALTER TABLE household_members
    ADD CONSTRAINT FK_HOUSEHOLD_MEMBERS_ON_HOUSEHOLD FOREIGN KEY (household_id) REFERENCES households (id);

ALTER TABLE household_members
    ADD CONSTRAINT FK_HOUSEHOLD_MEMBERS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE locations
    ADD CONSTRAINT FK_LOCATIONS_ON_HOUSEHOLD FOREIGN KEY (household_id) REFERENCES households (id);

ALTER TABLE locations
    DROP COLUMN location_type;

ALTER TABLE locations
    DROP COLUMN parent_location_id;