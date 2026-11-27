package com.example.retailstore.webapp.services;

import com.example.common.dto.PagedResult;
import com.example.common.logging.LogSanitizer;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.clients.order.CreateOrderRequest;
import com.example.retailstore.webapp.clients.order.OrderConfirmationDTO;
import com.example.retailstore.webapp.clients.order.OrderRequestExternal;
import com.example.retailstore.webapp.clients.order.OrderResponse;
import com.example.retailstore.webapp.clients.order.OrderServiceClient;
import com.example.retailstore.webapp.exception.InvalidRequestException;
import com.example.retailstore.webapp.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderServiceClient orderServiceClient;
    private final CustomerServiceClient customerServiceClient;
    private final SecurityHelper securityHelper;

    public OrderService(
            OrderServiceClient orderServiceClient,
            CustomerServiceClient customerServiceClient,
            SecurityHelper securityHelper) {
        this.orderServiceClient = orderServiceClient;
        this.customerServiceClient = customerServiceClient;
        this.securityHelper = securityHelper;
    }

    /**
     * Retrieves an order only if it belongs to the customer identified by the current user's
     * email, and adds that customer's details to the returned order.
     *
     * @param orderNumber value forwarded as the downstream order ID
     * @return the customer's order with customer details populated
     * @throws ResourceNotFoundException if ownership does not match or any exception occurs while
     *     retrieving or enriching the order
     */
    public OrderResponse getOrder(String orderNumber) {
        try {
            OrderResponse orderResponse = orderServiceClient.getOrder(orderNumber);
            String email = securityHelper.getLoggedInUserEmail();
            CustomerResponse loggedInCustomer = customerServiceClient.getCustomerByEmail(email);

            if (!orderResponse.getCustomerId().equals(loggedInCustomer.customerId())) {
                log.warn(
                        "User {} attempted to fetch order {} belonging to customer {}",
                        email,
                        orderNumber,
                        orderResponse.getCustomerId());
                throw new ResourceNotFoundException("Order", "orderNumber", orderNumber);
            }

            orderResponse.updateCustomerDetails(loggedInCustomer);
            return orderResponse;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error fetching order {}: {}",
                    LogSanitizer.sanitizeForLog(orderNumber),
                    LogSanitizer.sanitizeException(e));
            throw new ResourceNotFoundException("Order", "orderNumber", orderNumber);
        }
    }

    /**
     * Retrieves orders for the customer identified by the current user's email using downstream
     * default pagination. Customer lookup and order retrieval failures propagate to the caller.
     *
     * @return the customer's order page returned by the order service
     */
    public PagedResult<OrderResponse> getOrders() {
        String email = securityHelper.getLoggedInUserEmail();
        CustomerResponse loggedInCustomer = customerServiceClient.getCustomerByEmail(email);
        return orderServiceClient.getOrdersByCustomer(loggedInCustomer.customerId());
    }

    /**
     * Creates an order using the customer ID resolved from the current user's email.
     *
     * @param orderRequest items and delivery address to submit; customer details are not forwarded
     * @return the downstream order confirmation
     * @throws InvalidRequestException if customer lookup, request conversion, or order creation
     *     fails; existing instances propagate and other exceptions are converted to this type
     */
    public OrderConfirmationDTO createOrder(CreateOrderRequest orderRequest) {
        try {
            String email = securityHelper.getLoggedInUserEmail();
            CustomerResponse loggedInCustomer = customerServiceClient.getCustomerByEmail(email);

            OrderRequestExternal orderRequestExternal = orderRequest.withCustomerId(loggedInCustomer.customerId());
            return orderServiceClient.createOrder(orderRequestExternal);
        } catch (InvalidRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating order: {}", LogSanitizer.sanitizeException(e));
            throw new InvalidRequestException("Failed to create order. Please try again later.");
        }
    }
}
