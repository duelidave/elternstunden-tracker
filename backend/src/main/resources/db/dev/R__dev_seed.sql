-- Demo accounts for the local role switcher (D-26, FS-DEV 7.12). Applied only with the dev profile
-- (spring.flyway.locations in application-dev.yaml), never in production. Idempotent.
-- The master demo account comes from the configuration (.env.example), not from here.

INSERT INTO family (id, label)
VALUES ('00000000-0000-4000-8000-00000000f001', 'Familie Demo')
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_user (id, email, display_name, role, status, family_id, email_verified_at)
VALUES
    ('00000000-0000-4000-8000-00000000a001', 'familie@dev.local',      'Frieda Familie',   'FAMILY_MEMBER', 'ACTIVE', '00000000-0000-4000-8000-00000000f001', now()),
    ('00000000-0000-4000-8000-00000000a002', 'partner@dev.local',      'Paul Partner',     'FAMILY_MEMBER', 'ACTIVE', '00000000-0000-4000-8000-00000000f001', now()),
    ('00000000-0000-4000-8000-00000000a003', 'unzugeordnet@dev.local', 'Uli Unzugeordnet', 'FAMILY_MEMBER', 'ACTIVE', NULL,                                   now()),
    ('00000000-0000-4000-8000-00000000a004', 'admin@dev.local',        'Ada Admin',        'ADMIN',         'ACTIVE', NULL,                                   now())
ON CONFLICT (email) DO NOTHING;
