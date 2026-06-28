# Simple Wallet Service

A basic bookkeeping / wallet service implemented with Java and Spring Boot.

The service exposes a REST API for creating accounts, checking balances, depositing funds, withdrawing funds, transferring funds between accounts, and listing account transaction history.

## Technology stack

- Java 25
- Spring Boot
- Gradle
- Spring Web / Web MVC
- Spring Data JPA
- H2 in-memory database
- Bean Validation
- JUnit 5
- Mockito
- MockMvc

## Design overview

The application uses a hybrid bookkeeping model:

- `Account` stores the current account balance.
- `TransactionEntry` stores the accounting history for each balance-changing operation.

The current balance is stored directly on the account for efficient reads and safe insufficient-funds checks. Each balance-changing operation also writes a corresponding `TransactionEntry`, so the account history can be audited and the balance can be reconstructed if needed.

All balance-changing operations are executed inside database transactions.

## Concurrency strategy

Correctness is handled through database transactions and pessimistic row-level locking.

For deposit and withdrawal operations, the affected account is loaded using a pessimistic write lock before the balance is changed.

For transfers, both accounts are locked in deterministic ID order. This reduces the risk of deadlocks when two concurrent transfers operate on the same pair of accounts in opposite directions.

Transfer operations are atomic:

1. Lock both accounts.
2. Check source account balance.
3. Debit source account.
4. Credit target account.
5. Write `TRANSFER_OUT` and `TRANSFER_IN` transaction entries.
6. Commit the transaction.

If any step fails, the full operation is rolled back.

## Assumptions

- Single currency only.
- No overdrafts allowed.
- Amounts use two decimal places.
- Negative balances are not allowed.
- Authentication and authorization are out of scope.
- H2 in-memory database is used for local/demo execution.

## Known shortcuts

This implementation intentionally keeps some production concerns out of scope:

- No authentication or authorization.
- No idempotency keys for retry-safe client requests.
- No multi-currency handling.
- No external production database configuration.
- No database migration tool such as Flyway or Liquibase.
- No production observability, tracing, or metrics.
- No full audit/event-sourcing model.

In a production system, the same model should run against a transactional database such as PostgreSQL, with idempotency keys, authentication, database migrations, monitoring, and operational hardening.

## Build

```bash
./gradlew clean build
```

## Run

```bash
./gradlew bootRun
```

The application starts on:

```text
http://localhost:8080
```

## Run tests

```bash
./gradlew test
```

The test suite includes:

- Unit tests for service logic.
- Integration tests for database-backed use cases.
- API error handling tests.
- Concurrency tests for withdrawals and transfers.

## API examples

### Create account

```http
POST /api/accounts
```

Request:

```json
{
  "initialBalance": 100.00
}
```

Response:

```json
{
  "accountId": 1,
  "balance": 100.00
}
```

Example:

```bash
curl -X POST "http://localhost:8080/api/accounts" \
  -H "Content-Type: application/json" \
  -d '{"initialBalance":100.00}'
```

Create an account with zero balance:

```bash
curl -X POST "http://localhost:8080/api/accounts" \
  -H "Content-Type: application/json" \
  -d '{}'
```

---

### Get balance

```http
GET /api/accounts/{accountId}/balance
```

Example:

```bash
curl "http://localhost:8080/api/accounts/1/balance"
```

Response:

```json
{
  "accountId": 1,
  "balance": 100.00
}
```

---

### Deposit funds

```http
POST /api/accounts/{accountId}/deposit
```

Request:

```json
{
  "amount": 50.00,
  "description": "Top-up"
}
```

Example:

```bash
curl -X POST "http://localhost:8080/api/accounts/1/deposit" \
  -H "Content-Type: application/json" \
  -d '{"amount":50.00,"description":"Top-up"}'
```

Response:

```json
{
  "accountId": 1,
  "balance": 150.00
}
```

---

### Withdraw funds

```http
POST /api/accounts/{accountId}/withdraw
```

Request:

```json
{
  "amount": 25.00,
  "description": "Purchase"
}
```

Example:

```bash
curl -X POST "http://localhost:8080/api/accounts/1/withdraw" \
  -H "Content-Type: application/json" \
  -d '{"amount":25.00,"description":"Purchase"}'
```

Response:

```json
{
  "accountId": 1,
  "balance": 125.00
}
```

If the account has insufficient funds, the API returns:

```http
409 Conflict
```

Example error response:

```json
{
  "timestamp": "2026-06-28T12:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Account does not have enough funds: 1",
  "path": "/api/accounts/1/withdraw",
  "details": []
}
```

---

### Transfer funds

```http
POST /api/transfers
```

Request:

```json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 30.00,
  "description": "Wallet transfer"
}
```

Example:

```bash
curl -X POST "http://localhost:8080/api/transfers" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":30.00,"description":"Wallet transfer"}'
```

Response:

```json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 30.00,
  "fromAccountBalance": 95.00,
  "toAccountBalance": 130.00,
  "referenceId": "generated-reference-id"
}
```

Each transfer creates two transaction entries:

- `TRANSFER_OUT` for the source account.
- `TRANSFER_IN` for the target account.

Both entries share the same `referenceId`.

---

### List account transactions

```http
GET /api/accounts/{accountId}/transactions?page=0&size=20
```

Example:

```bash
curl "http://localhost:8080/api/accounts/1/transactions?page=0&size=10"
```

Response:

```json
{
  "accountId": 1,
  "transactions": [
    {
      "id": 3,
      "type": "WITHDRAWAL",
      "amount": 25.00,
      "balanceAfter": 125.00,
      "description": "Purchase",
      "referenceId": null,
      "createdAt": "2026-06-28T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

## Error response format

Errors are returned in a consistent JSON format:

```json
{
  "timestamp": "2026-06-28T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/accounts",
  "details": [
    "initialBalance: Initial balance cannot be negative"
  ]
}
```

Common status codes:

- `400 Bad Request` for invalid input.
- `404 Not Found` for missing accounts.
- `409 Conflict` for insufficient funds.
- `500 Internal Server Error` for unexpected errors.

## Project structure

```text
com.nemanjanedic.simplewallet

  account
    Account
    AccountRepository
    AccountService
    AccountController
    dto

  transaction
    TransactionEntry
    TransactionEntryRepository
    TransactionService
    TransactionController
    TransactionType
    dto

  transfer
    TransferService
    TransferController
    dto

  common
    ApiError
    GlobalExceptionHandler
    exception
```

## Main business rules

- Initial balance cannot be negative.
- Deposit amount must be greater than zero.
- Withdrawal amount must be greater than zero.
- Withdrawal cannot exceed available balance.
- Transfer amount must be greater than zero.
- Transfer source and target accounts must be different.
- Transfer cannot exceed source account balance.
- Amounts must have at most two decimal places.

## Testing strategy

The project includes multiple levels of tests:

- Unit tests for service-level business logic.
- Integration tests for persistence-backed flows.
- API error handling tests using MockMvc.
- Concurrency tests to verify correctness under parallel withdrawals and transfers.

The concurrency tests cover important wallet correctness scenarios:

- Concurrent withdrawals cannot overdraw an account.
- Only valid withdrawals succeed.
- Failed withdrawals do not corrupt the balance.
- Concurrent transfers preserve total funds across accounts.

## Production improvements

For a production-ready wallet system, the following improvements would be recommended:

- Use PostgreSQL or another production-grade transactional database.
- Add idempotency keys for client retries.
- Add authentication and authorization.
- Add database migrations using Flyway or Liquibase.
- Add structured logging, metrics, tracing, and alerting.
- Add API documentation using OpenAPI / Swagger.
- Add request correlation IDs.
- Add stronger audit and reconciliation processes.
- Add currency support if multi-currency accounts are required.
