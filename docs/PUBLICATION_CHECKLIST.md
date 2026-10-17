# GitHub publication checklist

## Recommended repository description

> AI-driven retail operations platform with Spring Boot, React, DynamoDB transactions, leakage-safe forecasting, idempotent AWS Lambda workflows, Terraform, and 88 verified tests.

Suggested topics: `java`, `spring-boot`, `react`, `typescript`, `dynamodb`, `aws-lambda`, `fastapi`, `scikit-learn`, `terraform`, `testcontainers`, `playwright`, `k6`, `machine-learning`, `portfolio-project`.

## Resume bullets

Use only claims you can defend from the repository and [test report](TEST_REPORT.md):

- Built a multi-store retail operations platform with Java 21/Spring Boot, React 19, DynamoDB, and FastAPI, enforcing JWT role/store isolation across catalog, inventory, order, analytics, and pricing workflows.
- Designed transactional order processing and store-scoped SKU uniqueness with DynamoDB conditional writes and optimistic versions, preventing partial fulfillment, negative stock, duplicate SKUs, and stale concurrent updates.
- Developed a leakage-safe demand-forecasting pipeline with chronological evaluation, shifted lag/rolling features, HistGradientBoosting, seasonal-naive benchmarking/fallback, and controlled cold-start/error behavior.
- Implemented an idempotent EventBridge/Lambda forecasting workflow with conditional run claims, durable DynamoDB/S3 results, encrypted/versioned storage, structured logs, retries, and Terraform-defined IAM/monitoring.
- Created an 88-test quality pipeline spanning JUnit/MockMvc/Testcontainers, pytest, React/Vitest, Playwright, and k6; achieved 90.36% Java, 91% Python, and 100% React line coverage, with 452/452 k6 checks and 52.78 ms dashboard p95 in the recorded smoke run.

Avoid phrases such as “deployed to AWS,” “production ML accuracy,” or “zero-downtime production system.” The live AWS sandbox check remains blocked and the data is synthetic.

## Pre-publication audit completed

- [x] README metrics map directly to `docs/TEST_REPORT.md`.
- [x] Local screenshots were captured from the genuine running application.
- [x] `.env`, Terraform state, model artifacts, test reports, coverage, browser binaries, dependency folders, caches, IDE files, logs, and generated Lambda ZIPs are ignored.
- [x] A targeted credential-pattern scan found no private key, GitHub/Slack/OpenAI token, or AWS access-key pattern in publishable source.
- [x] Demo credentials/default local secret are visibly labeled non-production.
- [x] License, security policy, contributing guide, architecture, test evidence, interview guide, and demo script exist.
- [x] Compose configuration parsed and the isolated local stack served authenticated API data.
- [ ] Replace `<YOUR_REPOSITORY_URL>` in the README after the GitHub repository is created.
- [ ] Confirm Git author name/email and the intended public account.
- [ ] Review every staged file and image before the first commit.
- [ ] Run the GitHub Actions workflow on the remote repository.
- [ ] Provision a disposable AWS sandbox only if `CLOUD-001` is to be completed; capture teardown evidence.

## Known unresolved items

1. `CLOUD-001` is **BLOCKED**: there has been no real EventBridge/Lambda/IAM/DynamoDB/S3/CloudWatch deployment test.
2. GitHub-hosted Actions have not run until the repository is pushed; current evidence is local/isolated execution plus workflow inspection.
3. The README clone URL is intentionally a placeholder until the final owner/repository name is known.
4. Dashboard history remains deterministic rather than derived from persisted order events.
5. The filtered product scan and demo identity/token-storage design require production hardening.

## Exact publication commands

Run these from the repository root. The commands create a reviewable first commit but do **not** push until the final explicit command.

```bash
git status --short --ignored
git diff --check
git add .
git status --short
git diff --cached --stat
git diff --cached -- . ':!docs/images/*.jpg'
git commit -m "Prepare Trendify portfolio documentation and verification evidence"

# Create an empty GitHub repository in the browser first, then replace placeholders:
git remote add origin https://github.com/<YOUR_GITHUB_USERNAME>/<REPOSITORY_NAME>.git
git remote -v
git branch -M main

# Push only after reviewing the staged/committed content and explicitly deciding to publish:
git push -u origin main
```

If `origin` already exists, inspect it with `git remote -v` and use `git remote set-url origin ...` only after confirming the destination. Never use `git push --force` for the initial publication.
