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

-- A child that already exists with the same name and birth date (e.g. entered by hand) is not added twice.
INSERT INTO child (id, family_id, first_name, last_name, birth_date, first_name_norm, last_name_norm)
SELECT v.id::uuid, v.family_id::uuid, v.first_name, v.last_name, v.birth_date::date, v.first_name_norm, v.last_name_norm
FROM (VALUES
    ('00000000-0000-4000-8000-00000000b001', '00000000-0000-4000-8000-00000000f001', 'Mila', 'Muster', '2018-03-14', 'mila', 'muster'),
    ('00000000-0000-4000-8000-00000000b002', '00000000-0000-4000-8000-00000000f001', 'Ben',  'Muster', '2020-07-02', 'ben',  'muster'),
    ('00000000-0000-4000-8000-00000000b003', '00000000-0000-4000-8000-00000000f002', 'Anna', 'Müller', '2017-05-03', 'anna', 'mueller'),
    ('00000000-0000-4000-8000-00000000b004', '00000000-0000-4000-8000-00000000f002', 'Emil', 'Müller', '2020-03-12', 'emil', 'mueller'),
    ('00000000-0000-4000-8000-00000000b005', '00000000-0000-4000-8000-00000000f002', 'Theo', 'Müller', '2025-02-14', 'theo', 'mueller'))
     AS v (id, family_id, first_name, last_name, birth_date, first_name_norm, last_name_norm)
WHERE NOT EXISTS (SELECT 1 FROM child c
                  WHERE c.first_name_norm = v.first_name_norm AND c.last_name_norm = v.last_name_norm
                    AND c.birth_date = v.birth_date::date)
ON CONFLICT DO NOTHING;

INSERT INTO child_enrollment (child_id, school_year_id)
SELECT c.id, sy.id
FROM child c JOIN school_year sy ON current_date BETWEEN sy.starts_on AND sy.ends_on
WHERE c.id IN ('00000000-0000-4000-8000-00000000b001', '00000000-0000-4000-8000-00000000b002',
               '00000000-0000-4000-8000-00000000b003', '00000000-0000-4000-8000-00000000b004',
               '00000000-0000-4000-8000-00000000b005')
ON CONFLICT DO NOTHING;

INSERT INTO registration_claim (id, user_id, child_first_name, child_last_name, child_birth_date, claim_key,
                                match_result)
VALUES ('00000000-0000-4000-8000-00000000d001', '00000000-0000-4000-8000-00000000a003', 'Mila', 'Mustr',
        '2018-03-14', encode(sha256(convert_to('mila|mustr|2018-03-14', 'UTF8')), 'hex'), 'NO_MATCH')
ON CONFLICT DO NOTHING;

-- M4: entries of "Familie Demo" in the current school year, two confirmed and one waiting for confirmation.
-- Dates lie between the start of the school year and today, never in the future.
INSERT INTO time_entry (id, family_id, school_year_id, created_by, work_date, start_time, duration_minutes, description,
                        status, confirmed_by, confirmed_at, created_at, updated_at)
SELECT e.id::uuid, '00000000-0000-4000-8000-00000000f001', sy.id, e.created_by::uuid,
       GREATEST(sy.starts_on, current_date - e.days_ago), e.start_time::time, e.minutes, e.description,
       e.status::entry_status,
       CASE WHEN e.status = 'CONFIRMED' THEN '00000000-0000-4000-8000-00000000a004'::uuid END,
       CASE WHEN e.status = 'CONFIRMED' THEN now() - interval '1 day' END,
       now() - make_interval(days => e.days_ago), now() - make_interval(days => e.days_ago)
FROM (VALUES
        ('00000000-0000-4000-8000-00000000e001', '00000000-0000-4000-8000-00000000a001', 21, NULL,    120,
         'Gartenaktion im Hort: Beete umgegraben', 'CONFIRMED'),
        ('00000000-0000-4000-8000-00000000e002', '00000000-0000-4000-8000-00000000a002', 12, '15:30', 90,
         'Kuchen für das Herbstfest gebacken', 'CONFIRMED'),
        ('00000000-0000-4000-8000-00000000e003', '00000000-0000-4000-8000-00000000a001', 2,  NULL,    60,
         'Fahrdienst zum Ausflug in den Wald', 'OPEN'))
     AS e (id, created_by, days_ago, start_time, minutes, description, status)
JOIN school_year sy ON current_date BETWEEN sy.starts_on AND sy.ends_on
ON CONFLICT DO NOTHING;
