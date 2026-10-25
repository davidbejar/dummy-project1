-- Screens of the frontend, and which roles may open each one. The backend checks the same
-- table before serving a screen's API, so a role sees exactly the screens linked to it here.
-- Adding a screen is data only: a row here, plus its rows in app_role_screen below.
CREATE TABLE app_screen (
    -- Matches the screen codes in frontend/src/app/screens.ts
    code         VARCHAR(30)  PRIMARY KEY CHECK (code = upper(code)),
    display_name VARCHAR(100) NOT NULL,
    -- Order of screens in lists and menus, e.g. the columns of the role administration screen
    sort_order   INT          NOT NULL,
    -- Spring path pattern of the API behind the screen, served only to roles that may open
    -- it. NULL for screens without an API of their own (login, the administration menu).
    api_path     VARCHAR(200) UNIQUE CHECK (api_path LIKE '/api/%')
);

INSERT INTO app_screen (code, display_name, sort_order, api_path) VALUES
    ('LOGIN', 'Login', 10, NULL),
    ('TODO', 'Todos', 20, '/api/todos/**'),
    ('ADMINISTRATION', 'Administration', 30, NULL),
    ('USER_ADMIN', 'User administration', 40, '/api/admin/users/**'),
    ('ROLE_ADMIN', 'Role administration', 50, '/api/admin/roles/**');

-- Visitors who are not signed in have this role.
INSERT INTO app_role (code, display_name) VALUES ('ANONYMOUS', 'Not logged');

CREATE TABLE app_role_screen (
    role_code   VARCHAR(20) NOT NULL REFERENCES app_role (code) ON DELETE CASCADE,
    screen_code VARCHAR(30) NOT NULL REFERENCES app_screen (code) ON DELETE CASCADE,
    -- A locked assignment cannot be removed in role administration.
    locked      BOOLEAN     NOT NULL DEFAULT false,
    PRIMARY KEY (role_code, screen_code)
);

INSERT INTO app_role_screen (role_code, screen_code, locked) VALUES
    -- Without the login screen nobody could sign in.
    ('ANONYMOUS', 'LOGIN', true),
    ('USER', 'TODO', false);

-- ADMIN sees every screen, and that cannot be changed. New screens need this row too.
INSERT INTO app_role_screen (role_code, screen_code, locked) SELECT 'ADMIN', code, true FROM app_screen;
