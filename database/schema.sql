DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS inventory;

CREATE TABLE inventory (
    product_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    stock INT NOT NULL
);

CREATE TABLE orders (
    order_id SERIAL PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO inventory (product_id, name, stock) VALUES
('P100', 'Wireless Mouse', 25),
('P200', 'Mechanical Keyboard', 10),
('P300', 'USB-C Hub', 0);

