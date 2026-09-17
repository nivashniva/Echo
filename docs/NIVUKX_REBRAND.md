# Nivukx Rebrand Record

## Scope

This change establishes **Nivukx** as the public application name while preserving existing runtime package identifiers and integration contracts.

## Compatibility Policy

- Existing Android application/package identifiers remain unchanged.
- Existing deep-link schemes, widget action contracts, service identifiers, and module namespaces remain unchanged unless explicitly required for compatibility.
- User-facing application naming resolves through `@string/app_name`.
- No existing feature implementation is removed as part of this rebrand.

## Current Public Name

`Nivukx`

## Rationale

The brand name is intentionally independent from the internal `Echo` implementation namespace so the application can be rebranded without destabilizing persisted data, Android component identity, integrations, or module boundaries.
