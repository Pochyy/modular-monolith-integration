DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS inventory;

CREATE TABLE inventory (
    product_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    stock INT NOT NULL
);

CREATE TABLE orders (
    order_id SERIAL PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    item_id SERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES orders(order_id),
    product_id VARCHAR(50) NOT NULL,
    quantity INT NOT NULL
);

CREATE TABLE notifications (
    notification_id SERIAL PRIMARY KEY,
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE supplier_orders (
    id SERIAL PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL,
    buyer_ref VARCHAR(80) NOT NULL UNIQUE,
    request_id VARCHAR(80) NOT NULL UNIQUE,
    po_number VARCHAR(50),
    cases INT NOT NULL DEFAULT 0,
    units INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO inventory (product_id, name, stock) VALUES
('P100', 'Wireless Mouse', 25),
('P200', 'Mechanical Keyboard', 10),
('P300', 'USB-C Hub', 0);

CREATE TABLE tiangge_feed_cursors (
    id SERIAL PRIMARY KEY,
    cursor_value VARCHAR(255) NOT NULL
);

CREATE TABLE tiangge_orders (
    tiangge_order_id VARCHAR(255) PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    placed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decision_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(50) NOT NULL,
    shop_order_id VARCHAR(255),
    reason TEXT,
    sync_status VARCHAR(50) NOT NULL
);

CREATE TABLE tiangge_order_items (
    item_id SERIAL PRIMARY KEY,
    tiangge_order_id VARCHAR(255) NOT NULL REFERENCES tiangge_orders(tiangge_order_id),
    seller_sku VARCHAR(255) NOT NULL,
    quantity INT NOT NULL
);

CREATE TABLE tiangge_pending_stock (
    seller_sku VARCHAR(255) PRIMARY KEY,
    available_quantity INT NOT NULL,
    last_updated TIMESTAMP WITH TIME ZONE
);