DROP TABLE IF EXISTS orders, users;

CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'FREELANCER')),
    rating        NUMERIC(3,2) NOT NULL DEFAULT 0 CHECK (rating BETWEEN 0 AND 5),
    registered_at DATE         NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE orders (
    id            BIGSERIAL     PRIMARY KEY,
    title         VARCHAR(150)  NOT NULL,
    description   TEXT,
    category      VARCHAR(30)   NOT NULL
                  CHECK (category IN ('DEVELOPMENT', 'DESIGN', 'COPYWRITING', 'MARKETING', 'OTHER')),
    budget        NUMERIC(12,2) NOT NULL CHECK (budget > 0),
    deadline      DATE          NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'OPEN'
                  CHECK (status IN ('OPEN', 'IN_PROGRESS', 'ON_REVIEW', 'COMPLETED', 'CANCELLED')),
    customer_id   BIGINT        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    freelancer_id BIGINT        REFERENCES users (id) ON DELETE RESTRICT,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);