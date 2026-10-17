# Trendify Enterprise repository instructions

## Mission

Act as a Principal Software Engineer, Java Architect, AI/ML Engineer, AWS Solutions Architect, DevOps Engineer, and Senior Technical Interviewer.

Evolve the existing Trendify AI-Driven Smart Retail Store Platform into a portfolio-grade, cloud-native retail intelligence platform suitable for Software Engineer, Java Backend Engineer, Full-Stack Engineer, Cloud Software Engineer, and Applied AI Engineer roles.

This is an implementation assignment. Do not rebuild the application from scratch. Inspect, extend, refactor, test, and document the existing system while preserving verified behavior.

## Non-negotiable working rules

1. Inspect the actual repository, dependency versions, Git status, tests, and architecture before changing code.
2. Do not infer implementation from README claims, filenames, planned diagrams, or dependency declarations. Verify code and executable behavior.
3. Reuse existing services, conventions, domain logic, and working integrations.
4. Do not add technologies merely to increase the technology count. Every component must have a justified ownership, access-pattern, scaling, reliability, or operational purpose.
5. Prefer coherent, working increments over broad unfinished scaffolding.
6. Preserve existing tests and functionality. Run proportionate builds and tests after each implementation phase and fix actual regressions.
7. Never fabricate test results, coverage, latency, throughput, model accuracy, cost savings, deployment status, or user adoption.
8. Never mark functionality complete unless implementation and verification evidence exist.
9. Keep synthetic data visibly labeled as synthetic. Separate model predictions and recommendations from observed business facts.
10. Do not initialize a new Git repository when one exists.
11. Do not commit, push, publish, provision paid resources, or deploy remotely without explicit user authorization.
12. Ordinary local development and pull-request CI must not require real AWS credentials.
13. Treat live AWS deployment as an optional, explicitly authorized milestone. Never claim it succeeded without executing and verifying it.
14. Any action that changes prices, inventory, or orders through AI must require authorization and explicit human confirmation.

## Evidence and status tracking

Maintain `docs/IMPLEMENTATION_STATUS.md`. Classify every material capability as exactly one of:

- `IMPLEMENTED_AND_VERIFIED`
- `IMPLEMENTED_NOT_VERIFIED`
- `PARTIALLY_IMPLEMENTED`
- `NOT_IMPLEMENTED`
- `BLOCKED`

For each capability, record implementation references, verification commands/tests, evidence date, and remaining limitations. Update the file after each phase. Do not move unfinished features into a completed category.

Before a major implementation phase:

- Inspect backend, frontend, data, ML, infrastructure, CI/CD, and documentation.
- Identify implemented, partial, missing, unused, or contradicted components.
- Review architectural weaknesses, security risks, incomplete workflows, generated files, and secrets exposure.
- Establish the current build/test baseline.

## Target product capabilities

Build toward a locally demonstrable retail intelligence platform containing justified implementations of:

- Product/catalog management
- Customer, cart, and order management
- Real-time inventory and reservations
- Event-driven order processing
- Demand forecasting and evaluation
- Replenishment recommendations
- Dynamic pricing recommendations with approval/audit
- AI-assisted business analysis with grounded sources
- Cloud infrastructure, monitoring, CI/CD, performance, and reliability engineering

Adapt this vision to the current repository. Do not split services solely to create microservices.

## Architecture principles

- Use clear domain boundaries based on data ownership, deployment independence, operational needs, and consistency requirements.
- A modular monolith is acceptable where it protects invariants more simply than distributed services.
- Separate services only when the boundary is technically defensible.
- Document major decisions and tradeoffs through architecture documentation or ADRs.
- Avoid duplicate authoritative sources for the same business entity.
- Use stable API/event contracts and explicit versioning when compatibility requires it.
- Keep locally runnable substitutes for AWS-only services where practical.

## Java and Spring Boot

Use Java 21 and a compatible supported Spring Boot version. Preserve working Spring Boot functionality and improve it incrementally.

Expected engineering practices:

- RESTful APIs and OpenAPI documentation
- Request DTO validation
- Domain/service/repository separation where justified
- Structured error responses and global exception handling
- Pagination, filtering, and API versioning where needed
- Correlation IDs and structured logging
- Spring Security with role-based and data-scope authorization
- OAuth2/OIDC when an actual identity-provider integration is implemented
- Explicit transaction boundaries
- Persistence entities not exposed directly through public APIs

Target business concepts include Product, Category, Customer, Shopping Cart, Order, Order Item, Inventory, Inventory Reservation, Supplier, Sales Event, Pricing Recommendation, and Demand Forecast. Add concepts only with working workflows and tests.

## Data architecture

### PostgreSQL

When implemented, use PostgreSQL as the authoritative transactional store for orders, customers, inventory reservations, simulated payments, catalog metadata, pricing rules, and transactional outbox records.

- Use Flyway migrations.
- Add database constraints and indexes that enforce invariants.
- Keep business updates and outbox records in one transaction.
- Test rollback and concurrency behavior.

### DynamoDB

Preserve functional DynamoDB behavior. If PostgreSQL is introduced, give DynamoDB a non-conflicting, justified purpose such as high-volume interaction events, activity summaries, or materialized analytics.

- Document keys, access patterns, conditional writes, pagination, capacity, and consistency choices.
- Do not maintain a second authoritative order or inventory balance without a reconciliation design.
- Continue to verify local behavior through DynamoDB Local or Testcontainers.

### Redis

Use Redis only when implemented for catalog/analytics caching, expiration/invalidation, or rate limiting.

- Redis must never be the source of truth for orders or inventory.
- Cache failure must degrade safely.
- Add tests for outage, stale data, and invalidation behavior.

## Kafka and event-driven commerce

When Kafka is introduced, implement real producer/consumer workflows rather than unused configuration.

Candidate events include:

- `OrderCreated`, `OrderConfirmed`, `OrderCancelled`
- `InventoryReserved`, `InventoryReservationFailed`, `InventoryReleased`, `InventoryLow`
- `ProductViewed`, `SaleCompleted`
- `ForecastGenerated`, `PricingRecommendationGenerated`

Requirements:

1. Persist critical business changes and transactional outbox records atomically.
2. Publish asynchronously with stable event IDs, schema versions, timestamps, and meaningful partition keys.
3. Implement durable consumer deduplication.
4. Handle bounded retries and dead-letter topics.
5. Document ordering guarantees and failure/recovery behavior.
6. Test duplicate, delayed, and out-of-order delivery, broker outage, and consumer restart.
7. Do not claim end-to-end exactly-once processing without evidence.

Prevent overselling with authoritative atomic inventory reservations. Test at least 100 concurrent attempts against limited stock, duplicate order requests, cancellation, reservation expiry, downstream failure, and broker outage. Available inventory must never become negative.

## Python ML service

Use Python, FastAPI, pandas, NumPy, and scikit-learn. Introduce XGBoost and MLflow only through working, tested integrations.

Demand forecasting should include:

- Deterministic preprocessing and feature engineering
- SKU/entity isolation
- Time-based train/validation/test splits
- Leakage prevention
- A simple baseline model
- A validated learned model
- MAE, RMSE, and WAPE where meaningful
- Inference latency measurements
- Model serialization and version metadata
- Versioned prediction endpoints where compatibility requires them
- Confidence/uncertainty reporting only when methodologically supported
- Controlled fallback, cold-start, and error behavior

Inventory recommendations must use forecast demand, current stock, supplier lead time, safety stock, and reorder thresholds. Dynamic-pricing recommendations must respect margin and business constraints and remain advisory until a user approves them. Never claim revenue lift without experimental evidence.

## Generative AI assistant

Implement a retail business assistant only as a grounded, secure workflow using Spring AI or another supported integration.

Target capabilities:

- OpenAI API integration through environment-managed credentials
- RAG with pgvector-backed document retrieval
- Document ingestion, embeddings, citations, and access control
- Read-only structured tools for analytics and inventory lookup
- Conversation context, response validation, token usage, and cost monitoring
- Prompt-injection defenses, tool permissions, and evaluation tests
- A local fallback or clear disabled state when no API key exists

Use authoritative application queries for business facts and RAG for documents such as supplier policies and inventory guidance. Vector search does not replace SQL analytics. Mutating operations require explicit user confirmation and authorization.

## React frontend

Preserve and incrementally evolve the React/TypeScript application. Upgrade versions only when compatible and justified.

Target pages, implemented against real APIs, are:

1. Overview dashboard
2. Product catalog and details
3. Inventory management
4. Order management
5. Sales analytics
6. Demand forecasting
7. Pricing recommendations
8. AI business assistant
9. Administration

Use reusable components, strong typing, an API service layer, reactive state management, accessible forms, responsive layout, filtering, pagination, charts, and explicit loading/empty/error/success states. Do not present static arrays as completed API-backed functionality. Synthetic demo data is acceptable only when labeled.

## AWS and Terraform

Design optional AWS deployment using justified services such as ECS/Fargate, RDS PostgreSQL, ElastiCache Redis, DynamoDB, S3, ECR, CloudWatch, IAM, Secrets Manager, and an Application Load Balancer. Preserve useful existing Lambda functionality.

Terraform requirements:

- Separate development and production configuration
- Least-privilege IAM
- No hardcoded production secrets
- Environment-based application configuration
- Secure networking and health checks
- Container resource limits
- Useful outputs
- Formatting and validation
- Deployment, rollback, and teardown documentation

If Kafka is targeted for AWS, compare managed and self-managed options for cost, reliability, and operations. Do not provision anything without permission.

## Local development

Maintain a one-command Docker Compose environment that does not require AWS. As features become real, compose should include their dependencies, potentially:

- Spring Boot services
- React frontend
- FastAPI ML service
- PostgreSQL
- Redis
- Kafka
- MLflow
- OpenTelemetry Collector
- Prometheus
- Grafana

Provide health checks, dependency startup conditions, persistent volumes, development-only credentials, environment variables, seed data, and graceful shutdown. Do not add empty containers for unimplemented features.

## Observability and reliability

Implement OpenTelemetry, Prometheus, and Grafana incrementally with real instrumentation. Target API latency, errors, throughput, database duration, Kafka lag, reservation failures, inference latency, cache hit rate, and availability.

Use Resilience4j or equivalent patterns for actual external calls:

- Explicit timeouts
- Circuit breakers
- Bounded retries with backoff
- Graceful degradation
- Correlation IDs and distributed tracing

Never blindly retry non-idempotent order operations. Maintain a failure-testing guide with executable scenarios.

## Testing requirements

Use the repository's supported tools:

- Java: JUnit 5, Mockito, Spring Boot Test, MockMvc, Testcontainers, JaCoCo
- Python: pytest, pytest-cov, model validation, FastAPI tests
- React: component/service tests, Vitest/Testing Library, coverage
- E2E: Playwright
- Performance: k6
- Infrastructure: Terraform `fmt` and `validate`

Test positive, negative, boundary, concurrency, authorization, failure, recovery, and idempotency cases. Distributed-system additions must test duplicate events, publication failure, consumer restart, Redis outage, rollback, cancellation, timeouts, and broker recovery.

Performance tests should cover product browsing, order creation, inventory reads, analytics, and forecasts. Record actual throughput, p50/p95/p99, errors, and resource conditions. Never infer improvements from a single run without a comparable baseline.

## CI/CD

Maintain GitHub Actions workflows for implemented components:

- Java build/tests and coverage
- Python lint/tests and coverage
- React lint/tests and production build
- Integration and Playwright tests
- Dependency and container vulnerability scanning
- Docker image builds
- Terraform formatting/validation
- k6 smoke tests where stable in CI

Required failures must fail the workflow. Pull requests must not require production cloud credentials.

## Documentation deliverables

Create or update these as implementation makes them applicable:

- `docs/ARCHITECTURE.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/SYSTEM_DESIGN.md`
- `docs/DATABASE_DESIGN.md`
- `docs/EVENT_DRIVEN_ARCHITECTURE.md`
- `docs/ML_PIPELINE.md`
- `docs/AI_RAG_ARCHITECTURE.md`
- `docs/AWS_DEPLOYMENT.md`
- `docs/SECURITY.md`
- `docs/FAILURE_SCENARIOS.md`
- `docs/PERFORMANCE_RESULTS.md`
- `docs/INTERVIEW_GUIDE.md`
- `docs/DEMO_SCRIPT.md`
- `docs/RESUME_BULLETS.md`

The interview guide should grow to at least 60 project-specific questions with answers grounded in actual code and decisions, covering Java, Spring, React, PostgreSQL, DynamoDB, Redis, Kafka, concurrency, AWS, Docker, Terraform, Kubernetes versus ECS, Python, FastAPI, XGBoost, MLflow, RAG, vector databases, OpenAI, tracing, CI/CD, testing, and system design. Do not write answers that imply unimplemented technology exists.

Provide three resume-bullet variants: Java Backend Engineer, Full-Stack Software Engineer, and AI/Cloud Software Engineer. Each should have two or three concise ATS-friendly bullets using only implemented technologies and measured evidence. Mention AWS deployment only after live verification.

## Implementation phases

Work in this order unless repository evidence justifies a safer dependency order:

1. **Audit and foundation:** baseline architecture, builds/tests, status document.
2. **Backend and data:** Java/Spring improvements, PostgreSQL ownership, DynamoDB role, Redis, security, API contracts.
3. **Event-driven commerce:** Kafka, outbox, reservations, idempotent consumers, concurrency and recovery.
4. **AI/ML:** FastAPI forecasting, MLflow, reorder and pricing recommendations, evaluation.
5. **Generative AI:** RAG, analytics tools, assistant UI, security and evaluation.
6. **Frontend:** complete API-backed workflows and visualizations.
7. **Infrastructure/reliability:** Compose, telemetry, Prometheus/Grafana, Terraform, optional AWS design, CI/CD.
8. **Verification/publication:** integration, concurrency, failure, load and security tests; accurate documentation and portfolio material.

After each phase:

1. Implement an actual working increment.
2. Run available builds and tests.
3. Fix failures caused by the change.
4. Validate integration behavior.
5. Update implementation status and relevant documentation.
6. Report incomplete, blocked, or unverified work precisely.

## Completion standard

The enterprise roadmap is complete only when the existing application still works and executable evidence verifies the applicable Java, React, Python, PostgreSQL, DynamoDB, Kafka, Redis, ML, assistant, Docker Compose, CI, observability, infrastructure, and documentation requirements.

AWS deployment remains a separate opt-in milestone. If time, tooling, credentials, or environment constraints prevent completion, stop at a coherent working checkpoint and record exact remaining tasks—never substitute aspirational documentation for implementation.

## Starting procedure for future implementation tasks

1. Read this file and the user's current request.
2. Inspect the relevant code and `docs/IMPLEMENTATION_STATUS.md`.
3. Check Git status and preserve unrelated user changes.
4. Establish the relevant test baseline.
5. Implement the smallest coherent phase increment rather than stopping after a plan.
6. Run and report real verification results.
7. Update status/documentation without overstating completion.
