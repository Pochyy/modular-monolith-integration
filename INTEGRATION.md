# LegacySupply Integration Discovery

## 1. Product Mapping
After authenticating and fetching the `/catalog` endpoint, the following mappings were identified between the internal product IDs and LegacySupply SKUs:
- **P100 (Wireless Mouse)** -> `NJL-7284` (PackSize: 24)
- **P200 (Mechanical Keyboard)** -> `NJL-2407` (PackSize: 6)
- **P300 (USB-C Hub)** -> `NJL-4240` (PackSize: 24)

## 2. Session Behavior
- **Authentication**: Using `POST /auth/token` with the client ID (student ID) and API key in XML format returns a short-lived `<SessionToken>`.
- **Lifetime**: The token seems to expire relatively quickly (observed ~10 minutes).
- **Header**: The token must be sent in the `X-LS-Session` header on subsequent requests.
- **Handling**: The HTTP client must automatically intercept 401 Unauthorized errors, request a new session token, and replay the original request transparently to avoid interrupting the application flow.

## 3. Error Codes Observed
- `401 Unauthorized`: Returns `E-AUTH-01 Credentials rejected` (wrong credentials) or happens when a session expires.
- `422 Unprocessable Entity`: Returns if the XML body is missing required fields, or the `Qty` is invalid (e.g., 0).
- `409 Conflict`: Returned if the exact same `X-Request-Id` is submitted but the payload/content is different (`E-IDEM-04`).
- `503 Service Unavailable`: `E-SYS-99 Service unavailable. Try later.` Occurs randomly during testing, requiring immediate retry logic.

## 4. Qty / Uom / PackSize Rules
- The `Qty` specified in the `PurchaseOrder` request represents **Cases (CS)**, not individual units.
- The `Uom` in the response is always `CS`.
- To order a specific number of units, the application must divide the needed units by the `PackSize`, **round up** to the next whole number, and submit that as the `Qty` (from 1 to 99).
- Upon delivery, the system must multiply the `Qty` of cases received by the `PackSize` to restock the correct number of single units in the inventory.

## 5. Unexpected Statuses
- LegacySupply purchase orders transition through `10` (Accepted), `20` (Picking), `30` (Shipped), and `40` (Delivered).
- The `checkOrderStatus` method translates these into the `SupplierOrderStatus` application enum.
- If an unknown or undocumented status code is received, the adapter logs a warning and returns `UNKNOWN`, preventing the system from falsely interpreting the order as delivered or rejected while preserving the last known valid state.

## 6. Resilience & Idempotency
- **Idempotency**: All `POST /purchase-orders` requests require an `X-Request-Id`. If the same ID is sent with the exact same payload, the API returns a 200 OK with the original `PurchaseOrderAck` instead of creating a duplicate order.
- **Retries**: A scheduled background job (`@Scheduled`) runs every 60 seconds to find all `PENDING` supplier orders in the database and re-submit them. Since the `BuyerRef` and `X-Request-Id` are persisted in the database *before* the first submission attempt, retries are naturally safe and idempotent, guaranteeing exactly-once creation even in the face of temporary `503` or network timeout errors.
