package com.example.stripedemo;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final String publishableKey;
    private final String webhookSecret;

    public PaymentController(PaymentService paymentService,
                             OrderRepository orderRepository,
                             @Value("${stripe.publishable-key}") String publishableKey,
                             @Value("${stripe.webhook-secret}") String webhookSecret) {
        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
        this.publishableKey = publishableKey;
        this.webhookSecret = webhookSecret;
    }

    /** Frontend asks for the PUBLISHABLE key (safe to share, it can only create card tokens). */
    @GetMapping("/config")
    public Map<String, String> config() {
        return Map.of("publishableKey", publishableKey);
    }

    /** STEP 1: frontend -> backend: "I want to pay X". Backend -> Stripe: create PaymentIntent. */
    @PostMapping("/payments")
    public ResponseEntity<Map<String, String>> createPayment(@RequestBody Map<String, Long> body) {
        Long amount = body.get("amountInCents");
        if (amount == null || amount < 50) { // Stripe minimum is 0.50 EUR
            return ResponseEntity.badRequest().body(Map.of("error", "amountInCents must be >= 50"));
        }
        try {
            String clientSecret = paymentService.createPayment(amount);
            return ResponseEntity.ok(Map.of("clientSecret", clientSecret));
        } catch (StripeException e) {
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * STEP 3: Stripe -> backend (webhook). This is the RELIABLE confirmation.
     * The frontend result alone is not trusted (user could close the browser, or fake it).
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload,
                                          @RequestHeader("Stripe-Signature") String signature) {
        Event event;
        try {
            // Verify the request really comes from Stripe
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        StripeObject object;
        try {
            // getObject() is empty if the account's API version differs from the SDK's -> fall back
            var deserializer = event.getDataObjectDeserializer();
            object = deserializer.getObject().isPresent()
                    ? deserializer.getObject().get()
                    : deserializer.deserializeUnsafe();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Cannot read event");
        }
        if (object instanceof PaymentIntent intent) {
            String orderId = intent.getMetadata().get("orderId");
            switch (event.getType()) {
                case "payment_intent.succeeded" -> paymentService.handlePaymentResult(orderId, true);
                case "payment_intent.payment_failed" -> paymentService.handlePaymentResult(orderId, false);
                default -> { /* other events ignored */ }
            }
        }
        return ResponseEntity.ok("received"); // 200 = Stripe stops retrying
    }

    /** Check an order's status (PENDING / PAID / FAILED). */
    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
