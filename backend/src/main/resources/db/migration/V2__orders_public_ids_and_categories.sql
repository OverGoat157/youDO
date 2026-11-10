CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO categories (code, name) VALUES
 ('DEVELOPMENT', 'Разработка'), ('DESIGN', 'Дизайн'),
 ('COPYWRITING', 'Тексты'), ('MARKETING', 'Маркетинг'), ('OTHER', 'Другое');

ALTER TABLE orders ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE orders ADD CONSTRAINT orders_public_id_unique UNIQUE (public_id);
ALTER TABLE orders ADD COLUMN code VARCHAR(50);
UPDATE orders SET code = 'LEGACY-' || id;
ALTER TABLE orders ALTER COLUMN code SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT orders_code_unique UNIQUE (code);
ALTER TABLE orders ADD COLUMN category_id UUID REFERENCES categories(id) ON DELETE RESTRICT;
UPDATE orders o SET category_id = c.id FROM categories c WHERE c.code = o.category;
ALTER TABLE orders ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE orders DROP COLUMN category;
ALTER TABLE orders ADD COLUMN urgent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE orders ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE orders ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE orders SET updated_at = created_at;
ALTER TABLE orders ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE orders ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE orders ADD CONSTRAINT orders_description_length CHECK (length(description) <= 5000);
CREATE TABLE order_tags (
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    tag VARCHAR(50) NOT NULL CHECK (tag = lower(btrim(tag)) AND length(tag) > 0 AND position('|' IN tag) = 0),
    PRIMARY KEY (order_id, tag)
);
CREATE INDEX orders_category_idx ON orders(category_id);
CREATE INDEX orders_customer_idx ON orders(customer_id);
CREATE INDEX orders_freelancer_idx ON orders(freelancer_id);
CREATE INDEX orders_status_archive_idx ON orders(archived, status);
CREATE INDEX orders_created_idx ON orders(created_at DESC, id);
CREATE INDEX orders_budget_idx ON orders(budget, id);
CREATE INDEX orders_deadline_idx ON orders(deadline, id);
CREATE INDEX orders_title_idx ON orders(lower(title), id);
CREATE INDEX orders_search_idx ON orders USING gin
 (to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(description,'') || ' ' || code));
CREATE INDEX order_tags_tag_idx ON order_tags(tag);
