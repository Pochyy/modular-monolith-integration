# Lab 3 Reflection

**1. LegacySupply never tells you how long a session lasts. Measure your session lifetime from your own logs, state the number, and explain how your adapter decides when to sign in again.**
Based on testing, the session token expires in approximately 10 to 11 minutes. My `LegacySupplyClient` handles this using a reactive, lazy-initialization approach. It does not run a timer to guess when the token expires. Instead, it attempts an API request using the current token; if LegacySupply returns a `401 Unauthorized` response, the client intercepts it, clears the invalid token, calls the `authenticate()` method to get a new session, and transparently replays the original request.

**2. The catalog reports PackSize and orders report Uom "CS". Using one of your own orders, show the arithmetic from "units your Inventory needed" to the Qty you sent, and to the units your Inventory received on delivery.**
When the inventory for `P100` (Wireless Mouse) falls below the threshold, the system requests a reorder of 20 units. From the catalog, the `PackSize` for `P100` (`NJL-7284`) is 24.
*   **Sent Qty**: We calculate the cases by dividing units needed by PackSize and rounding up: `ceil(20 / 24) = 1` case. Thus, we send `<Qty>1</Qty>` to LegacySupply.
*   **Received Units**: When the order is marked `DELIVERED`, the system receives 1 case. It calculates the restock amount as `1 case * 24 (PackSize) = 24 units`. The inventory is then replenished with exactly 24 units.

**3. Suppose LegacySupply is replaced next semester by a supplier with a JSON API and different status codes. List every class in your project that would have to change, and explain why your Order and Inventory modules are not on that list (or why they are).**
The classes that would need to change are completely contained within the `edu.cit.lariosa.supplier` module:
*   `LegacySupplyClient` (needs to change from XML to JSON and update API endpoints).
*   `LegacySupplyAdapter` (needs to change status mappings and parsing logic).
*   `ProductMapping` (needs to update the supplier SKU references if the new supplier uses different IDs).
*   If we rename the supplier, we might rename these classes, but the implementations would just adapt to the new API.

Our `Order` and `Inventory` modules **would not change at all**. This is the primary benefit of the Anti-Corruption Layer (ACL). The `SupplierGateway` interface, the `SupplierOrderResult`, and the domain event `SupplierDeliveryEvent` establish a strict boundary using pure application-level concepts (e.g., our internal product IDs and single units, rather than supplier-specific SKUs or case packaging). The core business domains remain isolated from the external integration details.
