# Modular Monolith Integration — Lab 1 + Lab 2

## Project Overview
This project is a Spring Boot application demonstrating a modular monolith architecture. It began in Lab 1 with an `Order` module and an `Inventory` module that integrate in-process, without HTTP/network calls between them. Lab 2 extends this with multi-item orders, order cancellation, a new `Notification` module driven by in-process domain events, and a low-stock auto-reorder signal. The application still uses a single Spring Boot service, a Supabase PostgreSQL database, and a React Vite frontend.

## Technologies Used
- Backend: Java 17, Spring Boot 3.2, Spring Data JPA, Spring's `ApplicationEventPublisher`, PostgreSQL Driver
- Frontend: React, Vite, Axios
- Database: Supabase PostgreSQL

## Project Structure
- `edu.cit.lariosa.shop` — Order module: controller, service, entities (`Order`, `OrderItem`), repository, DTOs
- `edu.cit.lariosa.inventory` — Inventory module: `InventoryService` interface, package-private `InventoryServiceImpl`, `Product` entity, controller, repository
- `edu.cit.lariosa.notification` — Notification module (new in Lab 2): entity, repository, controller, and an event-listening service
- `edu.cit.lariosa.events` — shared domain event classes (`OrderPlacedEvent`, `OrderRejectedEvent`, `LowStockEvent`) used by Order and Inventory to communicate with Notification without depending on it directly
- `edu.cit.lariosa.config` — `CorsConfig`, allowing requests from `http://localhost:5173`

## Architecture and Module Boundaries
All communication between modules stays in-process — there are no HTTP calls or message brokers between `shop`, `inventory`, and `notification`. Two enforcement mechanisms keep this strict:

**Order → Inventory**: Order depends only on the `InventoryService` interface via constructor injection. `InventoryServiceImpl` is package-private, so it physically cannot be imported, injected, or referenced from outside the `inventory` package. This means Order can never bypass the interface even by accident.

**Order/Inventory → Notification**: rather than calling `NotificationService` directly, `OrderService` publishes `OrderPlacedEvent` or `OrderRejectedEvent` through Spring's `ApplicationEventPublisher`, and `InventoryServiceImpl` publishes `LowStockEvent` when a reservation drops stock below the configured threshold. `NotificationService` listens for these using `@EventListener` and writes a row to the `notifications` table. Neither Order nor Inventory imports `NotificationService`, and `NotificationService` never calls back into `OrderService` or `InventoryService` — it only reads from `NotificationRepository`.

All `@EventListener` methods in this project are **synchronous** (no `@Async` is used anywhere). This means a notification is written to the database within the same request/transaction context as the order or inventory operation that triggered it — there is no eventual-consistency delay for notifications in this implementation.

## Supabase Setup
1. Create a project at https://supabase.com and wait for it to finish provisioning.
2. Open the SQL Editor and run the complete contents of `database/schema.sql`. This drops and recreates `inventory`, `orders`, `order_items`, and `notifications`, and seeds inventory with P100 (Wireless Mouse, 25), P200 (Mechanical Keyboard, 10), and P300 (USB-C Hub, 0). This is a manual, one-time step — the application itself never runs destructive SQL automatically.
3. Get your connection details by clicking **Connect** at the top of the Supabase dashboard.

**Manual step you may need — IPv6/direct connection issue:** Supabase's free-tier "Direct connection" hostname (`db.<project-ref>.supabase.co`) can resolve to an IPv6-only address, which fails to connect (`UnknownHostException`) on networks without IPv6 routing. If you hit this, switch to the **Session pooler** connection option in the same Connect panel instead — it's IPv4-compatible. Note that the pooler uses a different username format: `postgres.<project-ref>` instead of plain `postgres`.

## Required Environment Variables
- `DB_URL` — JDBC connection string, e.g. `jdbc:postgresql://<host>:5432/postgres` (direct) or `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres` (pooler)
- `DB_USERNAME` — `postgres` (direct) or `postgres.<project-ref>` (pooler)
- `DB_PASSWORD` — your database password

Set these in your shell before running Maven, or in your IDE's run configuration. Never commit them to a file that's tracked by Git.

## Running the Application

### Backend
```bash
cd backend
# set DB_URL, DB_USERNAME, DB_PASSWORD in this shell session first
./mvnw spring-boot:run
```
Wait for `Started ShopApplication` in the console before sending requests. The backend runs on `http://localhost:8080`.

### Frontend
```bash
cd frontend
npm install
npm run dev
```
Access the frontend at `http://localhost:5173`.

## API Documentation

### `GET /api/inventory`
Returns all products with current stock.

### `GET /api/orders`
Returns order history: order ID, status, reason, line items, and creation timestamp.

### `POST /api/orders`
Places a multi-item order. Every line is validated against current stock **before any inventory is reserved** — if any line fails, the entire order is rejected and nothing is reserved (no partial fulfillment).

**Request:**
```json
{
  "items": [
    { "productId": "P100", "quantity": 2 },
    { "productId": "P200", "quantity": 1 }
  ]
}
```

**Confirmed response:**
```json
{
  "orderId": 1,
  "status": "CONFIRMED",
  "reason": "Order placed successfully",
  "items": [
    { "productId": "P100", "outcome": "RESERVED" },
    { "productId": "P200", "outcome": "RESERVED" }
  ],
  "inventory": [
    { "productId": "P100", "name": "Wireless Mouse", "stock": 23 },
    { "productId": "P200", "name": "Mechanical Keyboard", "stock": 9 }
  ]
}
```

**Rejected response:**
```json
{
  "orderId": 2,
  "status": "REJECTED",
  "reason": "Insufficient stock for: P300",
  "items": [
    { "productId": "P100", "outcome": "AVAILABLE" },
    { "productId": "P300", "outcome": "INSUFFICIENT_STOCK" }
  ],
  "inventory": [
    { "productId": "P100", "name": "Wireless Mouse", "stock": 23 },
    { "productId": "P300", "name": "USB-C Hub", "stock": 0 }
  ]
}
```

### `POST /api/orders/{orderId}/cancel`
Cancels an order and restocks only what that order actually reserved.
- `200` with the updated order — successful cancellation, quantities restocked
- `404` — order does not exist
- `409` — order is already `CANCELLED`

A rejected order has nothing to restock, since it never reserved inventory in the first place.

### `GET /api/notifications`
Returns the notification feed (confirmed orders, rejected orders, low-stock alerts), newest first.

## Low-Stock Configuration
The low-stock threshold is configurable in `application.properties`:
```properties
inventory.low-stock-threshold=5
```
After any successful reservation, if remaining stock falls below this threshold, `InventoryServiceImpl` publishes a `LowStockEvent`, which `NotificationService` turns into a message like: *"Low stock: P200 (Mechanical Keyboard) has 4 units remaining. Reorder needed."* The frontend inventory table highlights any row below this threshold.

## Testing
The following 5 scenarios were manually executed against a live Supabase-backed instance and confirmed passing:

1. **Multi-item success** — ordered P100×1 + P200×1 → `CONFIRMED`, both quantities decremented correctly.
2. **No partial reservation** — ordered P100×1 + P300×1 (P300 out of stock) → `REJECTED`, P100's stock confirmed unchanged.
3. **Cancellation/restock** — cancelled a confirmed order → status `CANCELLED`, stock restored to pre-order levels; re-cancelling the same order returned `409`; cancelling a nonexistent order ID returned `404`.
4. **Notifications** — confirmed and rejected orders each produced a corresponding notification, retrievable via `GET /api/notifications`.
5. **Low stock** — an order that dropped a product below the configured threshold produced a `LowStockEvent`-driven notification, and the affected product was correctly reflected in `GET /api/inventory`.

To reproduce: use `Invoke-RestMethod` (PowerShell) or `curl`/Postman against the endpoints above, using the seed data (P100=25, P200=10, P300=0) and threshold=5.

## Network Tab Evidence
With both servers running, open browser DevTools → Network tab and capture:
1. A successful multi-item order — the `POST /api/orders` request and its `CONFIRMED` response body. (Multi-Order-Confirmation.png)
2. A rejected multi-item order — the same request type with an insufficient-stock line item. (Notification-LowStock.png)
3. A cancellation — the `POST /api/orders/{id}/cancel` request and response. ( Order Cancellation.png)
4. `GET /api/inventory` — request and response.
5. `GET /api/orders` — request and response.
6. `GET /api/notifications` — request and response, ideally after triggering a low-stock event.

Save these screenshots in this repository as instructed by the assignment.

---

## Reflection

**1. Atomicity.** In this in-process modular monolith, multi-item order placement is wrapped in a single `@Transactional` method. All validation happens first, against a single shared database connection and transaction; only if every line passes does the method proceed to reserve stock for each item. If any reservation unexpectedly fails after validation, an exception is thrown, and Spring rolls back the entire transaction automatically — every reservation made in that method call is undone as one atomic unit, because it's one database transaction. If Inventory were a separate networked service instead, this guarantee would disappear: a single ACID transaction can't span two independent databases over a network. The system would need a different atomicity strategy, most commonly the **saga pattern** — a sequence of local transactions, each with a corresponding **compensating transaction** to undo it if a later step fails. Placing a multi-item order would mean calling Inventory once per line item, and if item 3 of 5 failed, the saga would have to explicitly call a "release reservation" compensating action for items 1 and 2, rather than relying on an automatic rollback.

**2. Domain Events.** `OrderService` publishes `OrderPlacedEvent`/`OrderRejectedEvent` instead of calling `NotificationService` directly so that Order has zero knowledge of Notification's existence — Order's only dependency is Spring's generic `ApplicationEventPublisher`. This keeps the modules loosely coupled: Notification could be modified, replaced, or temporarily removed without ever touching `OrderService`. If Notification became a separate microservice, this event-publishing pattern would extend naturally rather than being thrown away — the same events would be serialized and published to a message broker (e.g., Kafka or RabbitMQ) instead of Spring's in-memory event bus, and the listener would become a separate consuming service. The architectural shape (publish once, listen elsewhere, no coupling to the listener) stays the same; what changes is the transport and the consistency guarantee, which becomes eventual rather than synchronous.

**3. Module Extraction.** Of the three modules, **Notification** is the one I'd extract first. It's the most natural boundary because it's already fully decoupled — it depends on nothing but domain events, and nothing depends on it. Extracting it wouldn't require touching Order or Inventory's logic at all, only their infrastructure: the `ApplicationEventPublisher` calls would need to publish to a message broker instead of the in-process event bus, and `NotificationService`'s `@EventListener` methods would become message consumers in a separate service with its own database for the `notifications` table.
