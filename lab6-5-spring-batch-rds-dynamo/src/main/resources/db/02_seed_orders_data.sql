-- seed 5000 customers
INSERT INTO customers(name, email)
SELECT 'Customer ' || g, 'cust' || g || '@example.com'
FROM generate_series(1, 5000) g;

-- seed 50_000 orders
INSERT INTO orders(customer_id, status, amount, created_at)
SELECT (1 + floor(random() * 5000))::int, -- respecting the total number of customers
    (ARRAY['PENDING', 'SHIPPED', 'DELIVERED'])[1 + floor(random()*3)::int], -- one of the three
    round((random()*490 + 10)::numeric, 2), -- a price between 10 and 499, with 2 decimal places. Fake data, it does not represent the sum of qty*price of the items.
    now() - (random() * interval '365 days') -- a random date between now and 365 days ago
FROM generate_series(1, 50000);

-- seed order_items
INSERT INTO order_items (order_id, product, qty, price)
SELECT o.id,
       'SKU-' || (1 + floor(random() * 200))::int, -- a stock id from 'SKU-1' to 'SKU-200'
        1 + floor(random() * 5)::int, -- the quantity of that item in the given order
        round((random() * 90 + 5):: numeric, 2) -- price of a single item
FROM orders o,
     LATERAL generate_series(1, (1 + floor(random()*3))::int); -- seed up to three items per order

SELECT (SELECT count(*) FROM customers)   as customers,
       (SELECT count(*) FROM orders)      as orders,
       (SELECT count(*) FROM order_items) as items;
