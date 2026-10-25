-- Roles move from a CHECK constraint on app_user.role to their own table, so new roles are
-- rows here and app_user.role can only name one that exists.
CREATE TABLE app_role (
    -- Spring Security authority without the ROLE_ prefix, e.g. ADMIN
    code         VARCHAR(20)  PRIMARY KEY CHECK (code = upper(code)),
    display_name VARCHAR(100) NOT NULL
);

INSERT INTO app_role (code, display_name) VALUES
    ('USER', 'User'),
    ('ADMIN', 'Administrator');

-- CockroachDB's name for the CHECK (role IN ('USER', 'ADMIN')) from V3.
ALTER TABLE app_user DROP CONSTRAINT check_role;

ALTER TABLE app_user ADD CONSTRAINT app_user_role_fkey FOREIGN KEY (role) REFERENCES app_role (code);
