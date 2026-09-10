package edu.cit.lariosa.shop;

import edu.cit.lariosa.inventory.InventoryService;
import edu.cit.lariosa.inventory.Product;
import edu.cit.lariosa.shop.dto.InventoryDto;
import edu.cit.lariosa.shop.dto.OrderRequest;
import edu.cit.lariosa.shop.dto.OrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        String productId = request.getProductId();
        int quantity = request.getQuantity();

        Product product = inventoryService.getItem(productId);
        if (product == null) {
            Order order = new Order(productId, quantity, "REJECTED", "Product not found");
            orderRepository.save(order);
            return new OrderResponse("REJECTED", "Product not found", null);
        }

        boolean reserved = inventoryService.reserve(productId, quantity);
        if (reserved) {
            Order order = new Order(productId, quantity, "CONFIRMED", "Order placed successfully");
            orderRepository.save(order);
            // Fetch updated product info
            Product updatedProduct = inventoryService.getItem(productId);
            InventoryDto invDto = new InventoryDto(updatedProduct.getProductId(), updatedProduct.getName(), updatedProduct.getStock());
            return new OrderResponse("CONFIRMED", "Order placed successfully", invDto);
        } else {
            Order order = new Order(productId, quantity, "REJECTED", "Insufficient stock");
            orderRepository.save(order);
            InventoryDto invDto = new InventoryDto(product.getProductId(), product.getName(), product.getStock());
            return new OrderResponse("REJECTED", "Insufficient stock", invDto);
        }
    }
}

