# ADR 001: Modular monolith for the core API

Status: Accepted

The inventory, catalog, analytics, and recommendation domains share transactional invariants and have modest independent scaling needs. A modular monolith keeps deployment and local operation simple while package boundaries preserve future extraction seams. ML inference remains a separate service because its runtime and scaling profile differ.

