ALTER TABLE products
    ADD data_source VARCHAR(255);

ALTER TABLE products
    ADD last_retry_attempt TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE products
    ADD requires_api_retry BOOLEAN;

ALTER TABLE products
    ADD retry_attempts INTEGER;

ALTER TABLE users
    ADD role VARCHAR(255);