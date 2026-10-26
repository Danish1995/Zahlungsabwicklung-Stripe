# Zahlungsabwicklung mit Stripe – Spring Boot

Test payments with a Stripe **test card** through a Spring Boot backend, including order status updates via Stripe webhooks.

**Tech stack:** Java 21, Spring Boot 3.3, Stripe Java SDK, Stripe.js, Maven

**Security:** API keys are set as environment variables and are not stored in the code.

## Payment flow

```
Browser (index.html)          Spring Boot backend               Stripe
  | POST /api/payments            |                               |
  |------------------------------>| create Order (PENDING)        |
  |                               | create PaymentIntent -------->|
  |      client_secret            |<------------------------------|
  |<------------------------------|                               |
  | confirmCardPayment(card) ------------------------------------>|  card data goes
  |<----------------------- status: succeeded --------------------|  directly to Stripe
  |                               |  WEBHOOK payment_intent.succeeded
  |                               |<------------------------------|
  |                               | verify signature, Order -> PAID
```

## Project structure

| File | Role |
|---|---|
| `StripeDemoApplication` | Starts Spring Boot and sets the Stripe secret key |
| `PaymentController` | REST endpoints: `/api/config`, `/api/payments`, `/api/webhook`, `/api/orders/{id}` |
| `PaymentService` | Creates the order and PaymentIntent (with idempotency key), processes webhook events |
| `Order` / `OrderRepository` | Order with status PENDING → PAID / FAILED (in-memory storage) |
| `static/index.html` | Test page with the Stripe card field |

## Run it

1. Create a free Stripe account → Dashboard → **Test mode** → Developers → API keys.
2. Set the keys as environment variables (PowerShell example):
   ```powershell
   $env:STRIPE_SECRET_KEY="sk_test_..."
   $env:STRIPE_PUBLISHABLE_KEY="pk_test_..."
   ```
   In IntelliJ: Run → Edit Configurations → Modify options → Environment variables.
3. Start the app: `mvn spring-boot:run` (or run `StripeDemoApplication` in IntelliJ).
4. Open http://localhost:8080 and pay with **4242 4242 4242 4242**, any future date, any CVC.
5. Check the payment in the Stripe Dashboard → Payments.

### Webhook (optional)

Install the [Stripe CLI](https://docs.stripe.com/stripe-cli), then run:

```
stripe login
stripe listen --forward-to localhost:8080/api/webhook
```

The CLI prints a `whsec_...` secret. Set it as `STRIPE_WEBHOOK_SECRET` and restart the app.
After a payment, the console shows: `Order ... -> PAID`.

## Test cards

| Card number | Result |
|---|---|
| 4242 4242 4242 4242 | Success |
| 4000 0000 0000 0002 | Declined |
| 4000 0025 0000 3155 | Requires 3D Secure authentication |
| 4000 0000 0000 9995 | Insufficient funds |

## Design decisions

- **Secret key only on the backend**; only the publishable key is used in the frontend.
- **Card data never reaches the server**: Stripe.js sends it directly to Stripe (PCI compliance).
- **Amounts in cents** (`long`), never `double`, to avoid rounding errors.
- **`metadata.orderId`** links each Stripe payment to the matching order.
- **Idempotency key**: a retried request never charges the customer twice.
- **Webhook as source of truth**: events are verified by signature, and duplicate events are ignored.