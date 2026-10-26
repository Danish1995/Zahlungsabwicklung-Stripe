package com.example.stripedemo;

/**
 * A very simple order. In a real shop this would be a JPA entity in PostgreSQL.
 * Amount is in CENTS (Stripe uses the smallest currency unit): 1999 = 19.99 EUR.
 */
public class Order {

    public enum Status { PENDING, PAID, FAILED }

    private final String id;
    private final long amountInCents;
    private final String currency;
    private String paymentIntentId;
    private Status status = Status.PENDING;

    public Order(String id, long amountInCents, String currency) {
        this.id = id;
        this.amountInCents = amountInCents;
        this.currency = currency;
    }

    public String getId() { return id; }
    public long getAmountInCents() { return amountInCents; }
    public String getCurrency() { return currency; }
    public String getPaymentIntentId() { return paymentIntentId; }
    public void setPaymentIntentId(String paymentIntentId) { this.paymentIntentId = paymentIntentId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
