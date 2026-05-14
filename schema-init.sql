CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    slug VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    logo_url TEXT,
    primary_color VARCHAR(7),
    secondary_color VARCHAR(7),
    accent_color VARCHAR(7),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID REFERENCES organizations(id),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    role_id BIGINT,
    active BOOLEAN DEFAULT true,
    email_verified BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

INSERT INTO roles (code, name) VALUES ('SUPERADMIN', 'Super Administrator'), ('ADMIN', 'Administrator'), ('MANAGER', 'Manager'), ('RECEPTIONIST', 'Receptionist') ON CONFLICT (code) DO NOTHING;

INSERT INTO organizations (slug, display_name) VALUES ('lasema', 'Lasema Spa') ON CONFLICT (slug) DO NOTHING;