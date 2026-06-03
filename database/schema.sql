-- ===========================================================
-- Туалеты Москвы — схема базы данных (PostgreSQL + PostGIS)
-- ===========================================================

-- 1. PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- 2. Enum type for toilet category
DO $$ BEGIN
    CREATE TYPE toilet_type AS ENUM ('FREE', 'PAID', 'TROIKA', 'MALL');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 3. Users
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- 4. Toilets (main table)
CREATE TABLE IF NOT EXISTS toilets (
    id            BIGSERIAL    PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    address       TEXT,
    -- PostGIS geography column (WGS-84, SRID 4326)
    -- Use GEOGRAPHY (not GEOMETRY) for accurate metre-based distance calculations
    location      GEOGRAPHY(POINT, 4326) NOT NULL,
    -- Redundant lat/lng columns for fast non-spatial queries and mobile client serialisation
    lat           DOUBLE PRECISION NOT NULL,
    lng           DOUBLE PRECISION NOT NULL,
    type          toilet_type  NOT NULL DEFAULT 'FREE',
    accessible    BOOLEAN      NOT NULL DEFAULT FALSE,
    rating        DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    rating_count  INTEGER      NOT NULL DEFAULT 0,
    working_hours VARCHAR(255),
    description   TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Ensure coordinates are within valid ranges
    CONSTRAINT chk_lat CHECK (lat BETWEEN -90  AND  90),
    CONSTRAINT chk_lng CHECK (lng BETWEEN -180 AND 180),
    CONSTRAINT chk_rating CHECK (rating BETWEEN 0 AND 5)
);

-- 5. Spatial index (GIST) — critical for ST_DWithin / ST_Distance performance
CREATE INDEX IF NOT EXISTS idx_toilets_location   ON toilets USING GIST (location);
-- Auxiliary B-tree indexes
CREATE INDEX IF NOT EXISTS idx_toilets_type       ON toilets (type);
CREATE INDEX IF NOT EXISTS idx_toilets_accessible ON toilets (accessible);
CREATE INDEX IF NOT EXISTS idx_toilets_rating     ON toilets (rating DESC);

-- 6. Reviews
CREATE TABLE IF NOT EXISTS reviews (
    id         BIGSERIAL   PRIMARY KEY,
    toilet_id  BIGINT      NOT NULL REFERENCES toilets(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    rating     SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (toilet_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_reviews_toilet ON reviews (toilet_id);
CREATE INDEX IF NOT EXISTS idx_reviews_user   ON reviews (user_id);

-- 7. Trigger: auto-update updated_at on toilets and users
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_toilets_updated_at
    BEFORE UPDATE ON toilets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 8. View: materialises the average rating for quick reads
CREATE OR REPLACE VIEW toilet_ratings AS
SELECT
    t.id,
    t.title,
    COALESCE(AVG(r.rating), t.rating) AS avg_rating,
    COUNT(r.id)                         AS review_count
FROM toilets t
LEFT JOIN reviews r ON r.toilet_id = t.id
GROUP BY t.id, t.title, t.rating;

-- 9. Function: update toilet.rating after each review insert/update/delete
CREATE OR REPLACE FUNCTION refresh_toilet_rating()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE toilets
    SET rating       = COALESCE((SELECT AVG(rating) FROM reviews WHERE toilet_id = COALESCE(NEW.toilet_id, OLD.toilet_id)), 0),
        rating_count = (SELECT COUNT(*)             FROM reviews WHERE toilet_id = COALESCE(NEW.toilet_id, OLD.toilet_id))
    WHERE id = COALESCE(NEW.toilet_id, OLD.toilet_id);
    RETURN NULL;
END;
$$;

CREATE OR REPLACE TRIGGER trg_reviews_refresh_rating
    AFTER INSERT OR UPDATE OR DELETE ON reviews
    FOR EACH ROW EXECUTE FUNCTION refresh_toilet_rating();
