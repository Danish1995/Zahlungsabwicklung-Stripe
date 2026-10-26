package com.example.stripedemo;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Business logic: create orders and talk to Stripe. */
@Service
public class PaymentService {

    private final OrderRepository orderRepository;

    public PaymentService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * STEP 1 of the payment flow:
     * create an order in OUR system, then create a PaymentIntent at Stripe.
     * Returns the client_secret that the frontend needs to confirm the payment.
     */
    public String createPayment(long amountInCents) throws StripeException {
        Order order = orderRepository.save(
                new Order(UUID.randomUUID().toString(), amountInCents, "eur"));

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(order.getAmountInCents())
                .setCurrency(order.getCurrency())
                .addPaymentMethodType("card")
                .putMetadata("orderId", order.getId())   // links Stripe payment <-> our order
                .build();

        // Idempotency key: if this request is retried (network error), Stripe will NOT create a 2nd payment
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("create-pi-" + order.getId())
                .build();

        PaymentIntent intent = PaymentIntent.create(params, options);

        order.setPaymentIntentId(intent.getId());
        orderRepository.save(order);

        return intent.getClientSecret();
    }

    /**
     * STEP 3 (called by the webhook): Stripe tells us the payment result.
     * Idempotent: if the order is already PAID, a duplicate event changes nothing.
     */
    public void handlePaymentResult(String orderId, boolean succeeded) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == Order.Status.PAID) {
                return; // already processed -> ignore duplicate webhook
            }
            order.setStatus(succeeded ? Order.Status.PAID : Order.Status.FAILED);
            orderRepository.save(order);
            System.out.println("Order " + orderId + " -> " + order.getStatus());
        });
    }
}
