package com.example.orderservice.services;

import java.math.BigDecimal;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderItem;

@Service
public class OrderGeneratorService {

	private static Random RAND = new Random();
	private AtomicLong id = new AtomicLong();
	private Executor executor;
	private KafkaTemplate<Long, Order> template;

	public OrderGeneratorService(Executor executor, KafkaTemplate<Long, Order> template) {
		this.executor = executor;
		this.template = template;
	}

	@Async("executor")
	public void generate() {
		for (int i = 0; i < 10000; i++) {
			Order o = new Order();
			o.setId(id.incrementAndGet());
			o.setStatus("NEW");
			o.setCustomerId(RAND.nextLong(100) + 1);
			OrderItem orderItem = new OrderItem();
			int x = RAND.nextInt(5) + 1;
			orderItem.setProductPrice(new BigDecimal(100 * x));
			orderItem.setQuantity(x);
			orderItem.setProductId(RAND.nextLong(100) + 1);
			o.addOrderItem(orderItem);
			template.send("orders", o.getId(), o);
		}
	}

}
