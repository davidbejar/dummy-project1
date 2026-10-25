-- Screens can sit under a parent screen, the menu through which they are reached: a role that
-- may open a screen may also open its parent, and losing a parent loses its children too.
-- Role administration keeps assignments that way.
ALTER TABLE app_screen ADD COLUMN parent_code VARCHAR(30) REFERENCES app_screen (code);

-- Screens only for visitors who are not signed in. They belong to ANONYMOUS alone and are not
-- shown in role administration.
ALTER TABLE app_screen ADD COLUMN anonymous_only BOOLEAN NOT NULL DEFAULT false;

UPDATE app_screen SET parent_code = 'ADMINISTRATION' WHERE code IN ('USER_ADMIN', 'ROLE_ADMIN');
UPDATE app_screen SET anonymous_only = true WHERE code = 'LOGIN';

DELETE FROM app_role_screen
WHERE role_code <> 'ANONYMOUS' AND screen_code IN (SELECT code FROM app_screen WHERE anonymous_only);

-- Roles that already open a child screen get its parent.
INSERT INTO app_role_screen (role_code, screen_code)
SELECT DISTINCT rs.role_code, s.parent_code
FROM app_role_screen rs JOIN app_screen s ON s.code = rs.screen_code
WHERE s.parent_code IS NOT NULL
ON CONFLICT (role_code, screen_code) DO NOTHING;
