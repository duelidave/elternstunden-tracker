-- Passkeys (7.2, D-30): the schema Spring Security's JDBC repositories expect, plus a foreign key so that removing
-- the user entity removes its credentials (anonymizing, DP-05). Only public keys are stored, never biometrics.
-- user_entities.id is the app_user.id as 16 bytes in Base64url, name the e-mail address, display_name the name.
CREATE TABLE user_entities (
    id           varchar(1000) PRIMARY KEY,
    name         varchar(320)  NOT NULL,   -- e-mail addresses are longer than the 100 of the Spring schema
    display_name varchar(200)
);

CREATE TABLE user_credentials (
    credential_id                varchar(1000) PRIMARY KEY,
    user_entity_user_id          varchar(1000) NOT NULL REFERENCES user_entities (id) ON DELETE CASCADE,
    public_key                   bytea         NOT NULL,
    signature_count              bigint,
    uv_initialized               boolean,
    backup_eligible              boolean       NOT NULL,
    authenticator_transports     varchar(1000),
    public_key_credential_type   varchar(100),
    backup_state                 boolean       NOT NULL,
    attestation_object           bytea,
    attestation_client_data_json bytea,
    created                      timestamp,
    last_used                    timestamp,
    label                        varchar(1000) NOT NULL
);
CREATE INDEX user_credentials_user_idx ON user_credentials (user_entity_user_id);
