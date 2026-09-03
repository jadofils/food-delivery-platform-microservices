-- Baseline roles per RULES.md §8, and a starting permission grant per role reflecting each
-- role's realistic responsibilities in the platform. Fine-grained and additive: growing a role's
-- access later is another migration inserting into role_permissions, never an application
-- code change (RULES.md §8: services authorize on permission strings, not role names).

INSERT INTO permissions (name, description) VALUES
    ('user:read',              'View user accounts'),
    ('user:manage',            'Create, update, lock/unlock user accounts'),
    ('role:manage',            'Create and modify roles, permissions, and their assignments'),
    ('order:create',           'Place an order'),
    ('order:cancel',           'Cancel an order'),
    ('order:read',             'View orders'),
    ('restaurant:menu:write',  'Create and modify a restaurant''s menu'),
    ('restaurant:menu:read',   'View a restaurant''s menu'),
    ('delivery:status:update', 'Update a delivery''s status'),
    ('delivery:read',          'View delivery records'),
    ('notification:read',      'View notification/audit records');

INSERT INTO roles (name, description) VALUES
    ('ADMIN',             'Full platform administrator'),
    ('CUSTOMER',          'Registered customer placing orders'),
    ('RESTAURANT_OWNER',  'Manages a restaurant and its menu'),
    ('DELIVERY_AGENT',    'Delivers orders and updates delivery status');

-- ADMIN: everything.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ADMIN';

-- CUSTOMER: browse, order, cancel.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'CUSTOMER' AND p.name IN ('order:create', 'order:cancel', 'order:read', 'restaurant:menu:read');

-- RESTAURANT_OWNER: manage own menu, see orders.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'RESTAURANT_OWNER' AND p.name IN ('restaurant:menu:write', 'restaurant:menu:read', 'order:read');

-- DELIVERY_AGENT: see and update deliveries, see the orders they're tied to.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'DELIVERY_AGENT' AND p.name IN ('delivery:status:update', 'delivery:read', 'order:read');
