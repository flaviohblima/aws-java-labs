CREATE TABLE IF NOT EXISTS customers
(
    id    serial PRIMARY KEY,
    name  TEXT NOT NULL,
    email TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS orders
(
    id          serial PRIMARY KEY,
    customer_id INT            NOT NULL REFERENCES customers (id),
    status      TEXT           NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL, -- total price of the order
    created_at  TIMESTAMPTZ      NOT NULL
);

CREATE TABLE IF NOT EXISTS order_items
(
    id       SERIAL PRIMARY KEY,
    order_id INT            NOT NULL REFERENCES orders (id),
    product  TEXT           NOT NULL,
    qty      INT            NOT NULL, -- quantity of that item in the order
    price    NUMERIC(12, 2) NOT NULL  -- price of a single item
);