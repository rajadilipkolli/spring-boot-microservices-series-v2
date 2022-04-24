package com.example.orderservice.services;

import com.example.orderservice.entities.Order;
import org.springframework.stereotype.Service;

@Service
public class OrderManageService {

  public Order confirm(Order orderPayment, Order orderStock) {
    Order o = new Order();
    o.setCustomerId(orderPayment.getCustomerId());
    o.setId(orderPayment.getId());
    o.setCustomerAddress(orderPayment.getCustomerAddress());
    o.setCustomerEmail(orderPayment.getCustomerEmail());
    o.setItems(orderPayment.getItems());
    if (orderPayment.getStatus().equals("ACCEPT") && orderStock.getStatus().equals("ACCEPT")) {
      o.setStatus("CONFIRMED");
    } else if (orderPayment.getStatus().equals("REJECT")
        && orderStock.getStatus().equals("REJECT")) {
      o.setStatus("REJECTED");
    } else if (orderPayment.getStatus().equals("REJECT")
        || orderStock.getStatus().equals("REJECT")) {
      String source = orderPayment.getStatus().equals("REJECT") ? "PAYMENT" : "STOCK";
      o.setStatus("ROLLBACK");
      o.setSource(source);
    }
    return o;
  }
}
