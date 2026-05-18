package com.redhat.coolstore.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import io.smallrye.mutiny.Uni;

import com.redhat.coolstore.model.Order;
import com.redhat.coolstore.utils.Transformers;

@ApplicationScoped
public class OrderServiceMDB {

	private static final Logger LOG = Logger.getLogger(OrderServiceMDB.class);

	@Inject
	OrderService orderService;

	@Inject
	CatalogService catalogService;

	@Incoming("orders")
	public Uni<Void> processOrder(Message<String> message) {
		String orderStr = message.getPayload();
		LOG.infof("Message received! Processing order: %s", orderStr);
		
		try {
			Order order = Transformers.jsonToOrder(orderStr);
			LOG.infof("Order object is %s", order);
			
			orderService.save(order);
			
			order.getItemList().forEach(orderItem -> {
				catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
			});
			
			return Uni.createFrom().completionStage(message.ack());
		} catch (Exception e) {
			LOG.errorf(e, "Error processing order message: %s", orderStr);
			return Uni.createFrom().completionStage(message.nack(e));
		}
	}
}