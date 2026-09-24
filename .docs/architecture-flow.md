# Architecture Flow

## High-Level System Architecture

```mermaid
graph TB
    Client[Client / API Consumer]
    
    subgraph Spring Boot Application
        Controller[Payment Controller]
        Idempotency[Idempotency Layer]
        PaymentService[Payment Service]
        StateMachine[State Machine]
        LedgerService[Ledger Service]
        OutboxWriter[Outbox Writer]
        OutboxPoller[Outbox Poller]
    end
    
    subgraph Docker
        DB[(PostgreSQL)]
        Kafka[Apache Kafka]
    end
    
    Consumer[Kafka Consumer / Logger]
    
    Client -->|REST API| Controller
    Controller --> Idempotency
    Idempotency -->|check/store key| DB
    Idempotency --> PaymentService
    PaymentService --> StateMachine
    PaymentService --> LedgerService
    LedgerService -->|write debit + credit| DB
    PaymentService --> OutboxWriter
    OutboxWriter -->|same transaction| DB
    OutboxPoller -->|poll unpublished| DB
    OutboxPoller -->|publish events| Kafka
    Kafka --> Consumer
```

## Complete Payment Flow: Create → Authorize → Settle

```mermaid
sequenceDiagram
    participant C as Client
    participant API as PaymentController
    participant IK as Idempotency Layer
    participant PS as PaymentService
    participant SM as State Machine
    participant LS as LedgerService
    participant DB as PostgreSQL
    participant OB as Outbox Poller
    participant K as Kafka
    participant KL as Kafka Consumer

    Note over C,KL: === STEP 1: Create Payment ===

    C->>API: POST /api/v1/payments<br/>Idempotency-Key: abc-123
    API->>IK: Check idempotency key
    IK->>DB: SELECT FROM idempotency_keys<br/>WHERE key = 'abc-123'
    DB-->>IK: Not found
    IK->>DB: INSERT idempotency_keys<br/>(key='abc-123', status='STARTED')
    IK->>PS: Process payment request
    PS->>DB: INSERT payments<br/>(status='INITIATED')
    PS->>DB: INSERT outbox_events<br/>(event_type='PAYMENT_CREATED')
    Note over PS,DB: Same DB transaction
    PS-->>IK: Payment created
    IK->>DB: UPDATE idempotency_keys<br/>SET status='COMPLETE', response={...}
    IK-->>API: Payment response
    API-->>C: 201 Created

    Note over C,KL: === STEP 2: Authorize Payment ===

    C->>API: POST /api/v1/payments/{id}/authorize
    API->>PS: Authorize payment
    PS->>DB: SELECT payment WHERE id={id}
    PS->>SM: Validate INITIATED → AUTHORIZED
    SM-->>PS: Valid transition
    PS->>DB: UPDATE payments SET status='AUTHORIZED'
    PS->>LS: Write authorization ledger entries
    LS->>DB: INSERT ledger_entries<br/>(account=source, type=DEBIT, amount=50)
    LS->>DB: INSERT ledger_entries<br/>(account=hold, type=CREDIT, amount=50)
    PS->>DB: INSERT outbox_events<br/>(event_type='PAYMENT_AUTHORIZED')
    Note over PS,DB: All in same DB transaction
    PS-->>API: Payment authorized
    API-->>C: 200 OK

    Note over C,KL: === STEP 3: Settle Payment ===

    C->>API: POST /api/v1/payments/{id}/settle
    API->>PS: Settle payment
    PS->>DB: SELECT payment WHERE id={id}
    PS->>SM: Validate AUTHORIZED → SETTLED
    SM-->>PS: Valid transition
    PS->>DB: UPDATE payments SET status='SETTLED'
    PS->>LS: Write settlement ledger entries
    LS->>DB: INSERT ledger_entries<br/>(account=hold, type=DEBIT, amount=50)
    LS->>DB: INSERT ledger_entries<br/>(account=destination, type=CREDIT, amount=50)
    PS->>DB: INSERT outbox_events<br/>(event_type='PAYMENT_SETTLED')
    Note over PS,DB: All in same DB transaction
    PS-->>API: Payment settled
    API-->>C: 200 OK

    Note over C,KL: === Background: Outbox Publishing ===

    loop Every few seconds
        OB->>DB: SELECT FROM outbox_events<br/>WHERE published_at IS NULL
        DB-->>OB: Unpublished events
        OB->>K: Publish to 'payment-events' topic
        OB->>DB: UPDATE outbox_events<br/>SET published_at = now()
        K->>KL: Deliver event
        KL->>KL: Log event
    end
```

## Idempotency Retry Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant API as PaymentController
    participant IK as Idempotency Layer
    participant DB as PostgreSQL

    C->>API: POST /api/v1/payments<br/>Idempotency-Key: abc-123
    API->>IK: Check idempotency key
    IK->>DB: SELECT FROM idempotency_keys<br/>WHERE key = 'abc-123'
    DB-->>IK: Found (status='COMPLETE',<br/>response={...})
    IK-->>API: Return cached response
    API-->>C: 201 Created (same response as first time)
    Note over C,DB: No new payment created,<br/>no new ledger entries,<br/>no new outbox event
```

## Ledger Balance Derivation

```mermaid
sequenceDiagram
    participant C as Client
    participant API as AccountController
    participant DB as PostgreSQL

    C->>API: GET /api/v1/accounts/{id}/balance
    API->>DB: SELECT SUM(<br/>CASE WHEN entry_type='CREDIT'<br/>THEN amount ELSE -amount END<br/>) FROM ledger_entries<br/>WHERE account_id = {id}
    DB-->>API: 150.0000
    API-->>C: {"accountId": "...", "balance": 150.0000}
    Note over C,DB: Balance is NEVER stored.<br/>Always derived from ledger entries.
```

## Money Flow Through Accounts

```mermaid
graph LR
    subgraph Authorization
        A1[Alice USER<br/>DEBIT $50] -->|$50| H1[Hold SYSTEM<br/>CREDIT $50]
    end

    subgraph Settlement
        H2[Hold SYSTEM<br/>DEBIT $50] -->|$50| B1[Bob USER<br/>CREDIT $50]
    end

    style A1 fill:#ff6b6b,color:#fff
    style H1 fill:#4ecdc4,color:#fff
    style H2 fill:#ff6b6b,color:#fff
    style B1 fill:#4ecdc4,color:#fff
```

| Step | Debit (money leaves) | Credit (money enters) | Sum |
|------|---------------------|----------------------|-----|
| Authorize | Alice -$50 | Hold +$50 | $0 |
| Settle | Hold -$50 | Bob +$50 | $0 |
| **Net** | **Alice -$50** | **Bob +$50, Hold $0** | **$0** |

## Failed Payment Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant API as PaymentController
    participant PS as PaymentService
    participant LS as LedgerService
    participant DB as PostgreSQL

    Note over C,DB: Payment was already AUTHORIZED<br/>(Alice debited, Hold credited)

    C->>API: POST /api/v1/payments/{id}/fail
    API->>PS: Fail payment
    PS->>DB: UPDATE payments SET status='FAILED'
    PS->>LS: Write reversal ledger entries
    LS->>DB: INSERT ledger_entries<br/>(account=hold, type=DEBIT, amount=50)
    LS->>DB: INSERT ledger_entries<br/>(account=source, type=CREDIT, amount=50)
    Note over LS,DB: Reverses the authorization:<br/>money returns to Alice
    PS->>DB: INSERT outbox_events<br/>(event_type='PAYMENT_FAILED')
    PS-->>API: Payment failed
    API-->>C: 200 OK
```