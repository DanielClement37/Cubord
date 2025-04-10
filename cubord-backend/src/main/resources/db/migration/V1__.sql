CREATE TABLE households
(
    id         UUID NOT NULL,
    name       VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_households PRIMARY KEY (id)
);

CREATE TABLE locations
(
    id                 UUID NOT NULL,
    name               VARCHAR(255),
    location_type      VARCHAR(255),
    parent_location_id UUID,
    created_at         TIMESTAMP WITHOUT TIME ZONE,
    updated_at         TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_locations PRIMARY KEY (id)
);

CREATE TABLE pantry_items
(
    id              UUID NOT NULL,
    product_id      UUID NOT NULL,
    location_id     UUID NOT NULL,
    expiration_date date,
    quantity        INTEGER,
    unit_of_measure VARCHAR(255),
    notes           VARCHAR(500),
    created_at      TIMESTAMP WITHOUT TIME ZONE,
    updated_at      TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_pantry_items PRIMARY KEY (id)
);

CREATE TABLE products
(
    id                      UUID NOT NULL,
    upc                     VARCHAR(255),
    name                    VARCHAR(255),
    brand                   VARCHAR(255),
    category                VARCHAR(255),
    default_expiration_days INTEGER,
    created_at              TIMESTAMP WITHOUT TIME ZONE,
    updated_at              TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_products PRIMARY KEY (id)
);

ALTER TABLE pantry_items
    ADD CONSTRAINT FK_PANTRY_ITEMS_ON_LOCATION FOREIGN KEY (location_id) REFERENCES locations (id);

ALTER TABLE pantry_items
    ADD CONSTRAINT FK_PANTRY_ITEMS_ON_PRODUCT FOREIGN KEY (product_id) REFERENCES products (id);