# Contributing

1. Create a focused branch and avoid committing secrets, generated models, coverage output, browser binaries, or Terraform state.
2. Add tests at the appropriate unit, integration, E2E, or performance layer.
3. Run the commands in the README test section. Docker is required for DynamoDB Local and the complete E2E stack.
4. Update `docs/TEST_PLAN.md` only from executed evidence; never mark blocked or unexecuted scenarios as passing.
5. Submit a pull request describing behavior, compatibility impact, verification commands, and any remaining operational risks.
