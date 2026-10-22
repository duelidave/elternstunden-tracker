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

-- M2: school years around today, children and enrollments, and an open claim with a typo for the unassigned
-- demo account (the admin dashboard suggests "Mila Muster" as candidate). Register yourself with the child
-- "Anna Müller", born 2017-05-03, to see an automatic assignment to "Familie Müller".
INSERT INTO school_year (id, label, starts_on, ends_on)
SELECT ('00000000-0000-4000-8000-00000000c00' || n)::uuid,
       y || '/' || lpad(((y + 1) % 100)::text, 2, '0'),
       make_date(y, 9, 1),
       make_date(y + 1, 8, 31)
FROM (SELECT n, extract(year FROM current_date - interval '8 months')::int - 1 + n AS y
      FROM generate_series(0, 1) AS n) AS years
ON CONFLICT DO NOTHING;

INSERT INTO family (id, label)
VALUES ('00000000-0000-4000-8000-00000000f002', NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO child (id, family_id, first_name, last_name, birth_date, first_name_norm, last_name_norm)
VALUES
    ('00000000-0000-4000-8000-00000000b001', '00000000-0000-4000-8000-00000000f001', 'Mila', 'Muster', '2018-03-14', 'mila', 'muster'),
    ('00000000-0000-4000-8000-00000000b002', '00000000-0000-4000-8000-00000000f001', 'Ben',  'Muster', '2020-07-02', 'ben',  'muster'),
    ('00000000-0000-4000-8000-00000000b003', '00000000-0000-4000-8000-00000000f002', 'Anna', 'Müller', '2017-05-03', 'anna', 'mueller')
ON CONFLICT DO NOTHING;

INSERT INTO child_enrollment (child_id, school_year_id)
SELECT c.id, sy.id
FROM child c JOIN school_year sy ON current_date BETWEEN sy.starts_on AND sy.ends_on
WHERE c.id IN ('00000000-0000-4000-8000-00000000b001', '00000000-0000-4000-8000-00000000b002',
               '00000000-0000-4000-8000-00000000b003')
ON CONFLICT DO NOTHING;

INSERT INTO registration_claim (id, user_id, child_first_name, child_last_name, child_birth_date, claim_key,
                                match_result)
VALUES ('00000000-0000-4000-8000-00000000d001', '00000000-0000-4000-8000-00000000a003', 'Mila', 'Mustr',
        '2018-03-14', encode(sha256(convert_to('mila|mustr|2018-03-14', 'UTF8')), 'hex'), 'NO_MATCH')
ON CONFLICT DO NOTHING;
