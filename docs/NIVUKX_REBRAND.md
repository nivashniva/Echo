# Nivukx Rebrand Record

## Scope

This change establishes **Nivukx** as the public application and product brand across the Android display name, user-facing copy, store metadata, documentation, release text, and supported public feature labels.

## Compatibility Policy

- Existing Android application/package identifiers remain unchanged.
- Existing deep-link schemes, widget action contracts, service identifiers, resource keys, and module namespaces remain unchanged.
- Kotlin class names, package paths, generated identifiers, and integration endpoints are technical compatibility surfaces and are not renamed merely for branding.
- User-facing application naming resolves through `@string/app_name`.
- No existing feature implementation is removed as part of this rebrand.

## Product Branding

- Application: `Nivukx`
- Music recognition: `Nivukx Find`
- Intelligent queue engine: `Nivukx Brain`
- Motion system branding: `Nivukx Motion`
- Crash report branding: `Nivukx Crash Report`

## Naming Rule

Human-facing occurrences of the legacy product name are replaced with **Nivukx**. Technical identifiers such as `echo.music.iad1tya`, `EchoMotion`, `EchoMusicWidgetManager`, resource keys, file paths, URLs, and external integration contracts remain unchanged unless a separate compatibility-safe migration is explicitly required.

## Rationale

The brand name is intentionally independent from the internal implementation namespace so the application can be rebranded without destabilizing persisted data, Android component identity, integrations, or module boundaries.
