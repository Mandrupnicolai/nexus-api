-- Seed admin user (password: Admin1234!)
INSERT INTO users (id, email, display_name, password_hash)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@nexusapi.dev',
    'NexusAdmin',
    '.vHHSaJmoBiPb0YLgNJVNbMkicqXjKcy'
);

-- Seed demo team
INSERT INTO teams (id, name, description, owner_id)
VALUES (
    '00000000-0000-0000-0000-000000000010',
    'Demo Team',
    'Seeded demo team',
    '00000000-0000-0000-0000-000000000001'
);

INSERT INTO team_members (user_id, team_id, role)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', 'OWNER');

-- Seed demo project
INSERT INTO projects (id, name, description, team_id, created_by)
VALUES (
    '00000000-0000-0000-0000-000000000020',
    'Demo Project',
    'Seeded demo project',
    '00000000-0000-0000-0000-000000000010',
    '00000000-0000-0000-0000-000000000001'
);

-- Seed sample tasks
INSERT INTO tasks (title, status, priority, project_id, created_by) VALUES
    ('Set up CI/CD pipeline',   'DONE',        3, '00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000001'),
    ('Design database schema',  'DONE',        3, '00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000001'),
    ('Implement JWT auth',       'IN_PROGRESS', 2, '00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000001'),
    ('Build Kanban board UI',   'TODO',        2, '00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000001'),
    ('Write integration tests', 'TODO',        1, '00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000001');