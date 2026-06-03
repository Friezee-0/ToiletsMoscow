-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- Enum for toilet type
DO $$ BEGIN
    CREATE TYPE toilet_type AS ENUM ('FREE', 'PAID', 'TROIKA', 'MALL');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Toilets table with PostGIS geography column
CREATE TABLE IF NOT EXISTS toilets (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    address      TEXT,
    location     GEOGRAPHY(POINT, 4326) NOT NULL,
    lat          DOUBLE PRECISION NOT NULL,
    lng          DOUBLE PRECISION NOT NULL,
    type         toilet_type NOT NULL DEFAULT 'FREE',
    accessible   BOOLEAN NOT NULL DEFAULT FALSE,
    rating       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rating_count INTEGER NOT NULL DEFAULT 0,
    working_hours VARCHAR(255),
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Reviews table
CREATE TABLE IF NOT EXISTS reviews (
    id         BIGSERIAL PRIMARY KEY,
    toilet_id  BIGINT NOT NULL REFERENCES toilets(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    rating     SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (toilet_id, user_id)
);

-- Spatial index for ST_DWithin / ST_Distance queries
CREATE INDEX IF NOT EXISTS idx_toilets_location ON toilets USING GIST (location);

-- Auxiliary indexes
CREATE INDEX IF NOT EXISTS idx_toilets_type       ON toilets (type);
CREATE INDEX IF NOT EXISTS idx_toilets_accessible ON toilets (accessible);
CREATE INDEX IF NOT EXISTS idx_reviews_toilet     ON reviews (toilet_id);
CREATE INDEX IF NOT EXISTS idx_reviews_user       ON reviews (user_id);

-- Trigger: auto-update updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_toilets_updated_at
    BEFORE UPDATE ON toilets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
