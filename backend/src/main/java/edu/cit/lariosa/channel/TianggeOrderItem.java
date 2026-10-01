package edu.cit.lariosa.channel;

import jakarta.persistence.*;

@Entity
@Table(name = "tiangge_order_items")
class TianggeOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @ManyToOne
    @JoinColumn(name = "tiangge_order_id", nullable = false)
    private TianggeOrder order;

    @Column(name = "seller_sku", nullable = false)
    private String sellerSku;

    private int quantity;

    public TianggeOrderItem() {}

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public TianggeOrder getOrder() { return order; }
    public void setOrder(TianggeOrder order) { this.order = order; }
    public String getSellerSku() { return sellerSku; }
    public void setSellerSku(String sellerSku) { this.sellerSku = sellerSku; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
