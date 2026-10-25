-- Development-only users. Not loaded when spring.flyway.locations excludes db/dev-data.
-- Passwords: admin/admin and david/david (bcrypt hashes).
INSERT INTO app_user (username, password_hash, display_name, role) VALUES
    ('admin', '{bcrypt}$2a$10$2qosZwVqp8raTjPVkIJeZeAQ3ErucU9/HsXBRmatWapOmwQtADJnK', 'Administrator', 'ADMIN'),
    ('david', '{bcrypt}$2a$10$YVGE4yPaub3W4Jra.n9.qONwNrYvOviaI.I46mkDeJg1XYesZcwQO', 'David Bejar', 'USER')
ON CONFLICT (username) DO NOTHING;
