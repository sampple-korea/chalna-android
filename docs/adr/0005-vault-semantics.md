# ADR 0005: Chalna Vault semantics

## Context

Users need app-contained storage without misleading security claims.

## Decision

Store Vault media in the app-specific external Movies subtree, expose only exact files through a narrow FileProvider, and describe uninstall deletion plainly.

## Alternatives

Internal storage would constrain large video capacity; custom encryption was outside the product/security scope.

## Consequences

Vault is hidden from normal Gallery and removed with app data, but it is not protection from a compromised OS.

## Verification

Canonical-path instrumentation, provider XML audit, share tests, uninstall documentation, and no silent destination fallback.
