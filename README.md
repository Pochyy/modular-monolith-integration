# Modular Monolith Integration

## Project Overview
This project is a Spring Boot application demonstrating a modular monolith architecture. It contains an `Order` module and an `Inventory` module that integrate in-process without relying on HTTP/network calls. The application uses a Supabase PostgreSQL database for data persistence and a React Vite frontend for the user interface.

## Technologies Used
- Backend: Java 17, Spring Boot, Spring Data JPA, PostgreSQL Driver
- Frontend: React, Vite, Axios
- Database: Supabase PostgreSQL

## Project Structure
The backend codebase is organized by modules:
- `edu.cit.lariosa.shop`: The Order module containing controllers, services, entities, repositories, and DTOs.
- `edu.cit.lariosa.inventory`: The Inventory module containing the inventory service, implementation, entity, and repository.

## Architecture and Module Boundary
The Order module depends only on the `InventoryService` interface via constructor injection. The implementation, `InventoryServiceImpl`, is intentionally `package-private`. This enforces a strict architectural boundary by preventing the Order module from importing or depending on the internal implementation of the Inventory module. 

## Supabase Setup
1. Create a Supabase project at https://supabase.com
2. Go to the SQL Editor in your Supabase dashboard.
3. Copy the contents of `database/schema.sql` and run the script to create the `inventory` and `orders` tables and seed the initial data.
4. Retrieve your database connection string, username, and password from the Database Settings page.

## Required Environment Variables
To run the backend, set the following environment variables:
- `DB_URL`: The JDBC connection string (e.g., `jdbc:postgresql://<host>:5432/<db>`)
- `DB_USERNAME`: Your database username (e.g., `postgres`)
- `DB_PASSWORD`: Your database password

You can place these in your run configuration or IDE environment variables.

## Running the Application
### Backend
1. Open the `backend` folder.
2. Set the required environment variables.
3. Run the Spring Boot application using your IDE or Maven:
   ```bash
   ./mvnw clean compile spring-boot:run
   ```

### Frontend
1. Open the `frontend` folder in your terminal.
2. Install dependencies (if not done yet):
   ```bash
   npm install
   ```
3. Start the Vite development server:
   ```bash
   npm run dev
   ```
4. Access the frontend at `http://localhost:5173`.

## API Documentation
### POST /api/orders
Places a new order.

**Request:**
```json
{
  "productId": "P100",
  "quantity": 2
}
```

**Confirmed Response:**
```json
{
  "status": "CONFIRMED",
  "reason": "Order placed successfully",
  "inventory": {
    "productId": "P100",
    "name": "Wireless Mouse",
    "stock": 23
  }
}
```

**Rejected Response:**
```json
{
  "status": "REJECTED",
  "reason": "Insufficient stock",
  "inventory": {
    "productId": "P300",
    "name": "USB-C Hub",
    "stock": 0
  }
}
```

## Testing and Network Tab Evidence
1. Launch both backend and frontend.
2. Open your browser's Developer Tools -> Network tab.
3. For a **CONFIRMED** order, select `P100 - Wireless Mouse`, enter quantity 2, and submit. Capture a screenshot of the Network Tab showing the successful POST request and response payload. Place the screenshot in this repository 
4. For a **REJECTED** order, select `P300 - USB-C Hub`, enter quantity 1, and submit. Capture a screenshot of the Network Tab showing the rejected response payload. Place the screenshot in this repository 

---

## Reflection

1. **What differs between integrating Order/Inventory in-process versus as separate microservices over a network?**
Integrating in-process provides the benefit of single transaction management (using `@Transactional` across module calls), significantly lower latency, and simplicity in deployment. You get strongly typed interfaces and compile-time guarantees for free without needing complex serialization/deserialization schemas (like JSON/protobuf) or network resilience patterns (like retries, circuit breakers, and distributed tracing). If these modules were split into separate microservices, we would need to add network communication logic (HTTP clients/message queues), deal with eventual consistency across distributed databases, add robust error handling for network timeouts, and deploy/manage multiple applications separately.

2. **Why does package-private visibility on InventoryServiceImpl matter for the module boundary?**
Package-private visibility ensures that external modules (like Order) can only interact with the Inventory module through explicitly defined public contracts (the `InventoryService` interface). If `InventoryServiceImpl` were public, developers might bypass the interface and inject the implementation directly into the Order module. This creates tight coupling to the concrete implementation details of the Inventory module. By keeping it package-private, we enforce loose coupling, making it much easier to change the inventory implementation (e.g., swapping out the database logic) without breaking the order module.

3. **When would you extract Inventory into its own microservice?**
Extracting Inventory into its own microservice would make sense when the two modules have drastically different scaling, deployment, or technological requirements. For instance, if the Inventory module needs to handle millions of read requests per second from various external systems and requires scaling out independently of the Order module. To accomplish this extraction, the code would change significantly: the in-process method call would be replaced with an HTTP REST client or a message broker publisher; the shared database would be split into two separate databases (one for orders, one for inventory); and the strict ACID transaction across the modules would likely be replaced with an eventual consistency pattern such as the Saga pattern.

