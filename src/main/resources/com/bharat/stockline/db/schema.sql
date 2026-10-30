-- Stockline schema for MySQL 8.0.16+
-- Statements are separated by lines containing only "-- @@" so the stored procedure body
-- (which contains semicolons) can be sent to JDBC as one statement.

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(120) NOT NULL UNIQUE,
    name          VARCHAR(80)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM('ADMIN', 'DEMO') NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)
-- @@
CREATE TABLE IF NOT EXISTS stores (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(60)  NOT NULL,
    city       VARCHAR(40)  NOT NULL,
    address    VARCHAR(160) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)
-- @@
CREATE TABLE IF NOT EXISTS products (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    sku         VARCHAR(24)  NOT NULL UNIQUE,
    category    VARCHAR(30)  NOT NULL,
    price_paise BIGINT       NOT NULL,
    description VARCHAR(300) NOT NULL DEFAULT '',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_price CHECK (price_paise > 0)
)
-- @@
CREATE TABLE IF NOT EXISTS inventory (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id      BIGINT NOT NULL,
    product_id    BIGINT NOT NULL,
    quantity      INT    NOT NULL,
    reorder_level INT    NOT NULL DEFAULT 10,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_store_product UNIQUE (store_id, product_id),
    CONSTRAINT chk_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_reorder CHECK (reorder_level >= 0),
    CONSTRAINT fk_inv_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
)
-- @@
CREATE TABLE IF NOT EXISTS orders (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id       BIGINT NULL,
    store_name     VARCHAR(60)  NOT NULL,
    customer_name  VARCHAR(60)  NOT NULL,
    customer_email VARCHAR(120) NOT NULL DEFAULT '',
    total_paise    BIGINT       NOT NULL,
    placed_by      VARCHAR(120) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_orders_created (created_at),
    CONSTRAINT fk_order_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE SET NULL
)
-- @@
CREATE TABLE IF NOT EXISTS order_items (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id         BIGINT NOT NULL,
    product_id       BIGINT NULL,
    product_name     VARCHAR(80) NOT NULL,
    sku              VARCHAR(24) NOT NULL,
    quantity         INT    NOT NULL,
    unit_price_paise BIGINT NOT NULL,
    CONSTRAINT chk_item_qty CHECK (quantity > 0),
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL
)
-- @@
CREATE TABLE IF NOT EXISTS reviews (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id    BIGINT NOT NULL,
    customer_name VARCHAR(60)  NOT NULL,
    rating        TINYINT      NOT NULL,
    comment       VARCHAR(500) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
)
-- @@
CREATE TABLE IF NOT EXISTS activity (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    type       VARCHAR(20)  NOT NULL,
    message    VARCHAR(300) NOT NULL,
    actor      VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity_created (created_at)
)
-- @@
-- Stock lines with product and store details and a computed status
CREATE OR REPLACE VIEW v_inventory_status AS
SELECT i.id            AS inventory_id,
       i.store_id,
       s.name          AS store_name,
       i.product_id,
       p.name          AS product_name,
       p.sku,
       p.category,
       i.quantity,
       i.reorder_level,
       CASE WHEN i.quantity = 0 THEN 'OUT'
            WHEN i.quantity <= i.reorder_level THEN 'LOW'
            ELSE 'OK' END AS status
FROM inventory i
JOIN stores s   ON s.id = i.store_id
JOIN products p ON p.id = i.product_id
-- @@
DROP PROCEDURE IF EXISTS sp_restock
-- @@
-- Adds stock and logs it in one call; used through JDBC's CallableStatement
CREATE PROCEDURE sp_restock(IN p_inventory_id BIGINT, IN p_units INT, IN p_actor VARCHAR(120))
BEGIN
    IF p_units < 1 OR p_units > 10000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Restock between 1 and 10,000 units.';
    END IF;

    UPDATE inventory SET quantity = quantity + p_units WHERE id = p_inventory_id;
    IF ROW_COUNT() = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'That stock line no longer exists.';
    END IF;

    INSERT INTO activity (type, message, actor)
    SELECT 'RESTOCK', CONCAT('Restocked ', p_units, ' x ', v.product_name, ' at ', v.store_name), p_actor
    FROM v_inventory_status v
    WHERE v.inventory_id = p_inventory_id;
END
