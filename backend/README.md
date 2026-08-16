# SmartLab backend database bootstrap

PostgreSQL is the canonical SmartLab database. Hibernate runs with
`ddl-auto=none`, so it does not create, update, or own the schema.

Run the SQL files in this order against a new, explicitly selected PostgreSQL
database:

1. `sql/001_smartlab_postgres_schema_seed.sql`
2. `sql/002_social_feed_interactions.sql`

Use your normal secure PostgreSQL credential mechanism; do not place database
passwords in shell history, command output, tickets, or documentation.

## Fresh bootstrap and current-lineage upgrades

On an empty database, `001` creates the current backend schema, roles,
permissions, and foundational seed data. `002` adds the social-feed reaction
and comment tables. Both scripts are designed to be re-run safely for the
current `001`/`002` lineage.

`001` also contains guarded upgrades for older forms of this same lineage:

- `posts.summary` is copied to `posts.excerpt`, then removed; missing slugs use
  deterministic `post-<id>` values.
- Legacy post statuses outside the canonical `PostStatus` values (including
  `ARCHIVED`) stop the upgrade with an actionable error. They are not silently
  reclassified because their business meaning is not equivalent.
- `post_reviews.reject_reason` and `reviewed_at` become `reason` and
  `created_at`. A legacy rejected review without a non-blank reason stops the
  upgrade; it is neither reclassified nor given fabricated text.
- `notifications.link_url` becomes `target_url`; legacy rows receive the
  deterministic `type` value `LEGACY`, because the current entity requires a
  non-null type.
- Legacy reset-OTP epoch milliseconds are converted to `TIMESTAMPTZ`.
- Legacy events without the newer fields receive `IN_PERSON` mode and
  `SCHEDULED` status as a documented compatibility assumption.

The script refuses to truncate legacy title, excerpt, or review-reason values.
It also refuses duplicate slugs or a generated `post-<id>` slug collision.
These failures roll back the relevant migration block, preserving existing data
and constraints for intentional remediation before retrying. `smartlab_db` is
not a source or target for this migration: it is a different legacy lineage
with UUID/lab-oriented schema and unrelated RBAC.

## RBAC seed

The canonical system roles are `ADMIN`, `LEADER`, and `MEMBER`. The seed
contains 21 canonical permissions and exactly 42 role-permission mappings:

- `ADMIN`: 20 mappings, including review and publish permissions, but not
  `posts.submit`.
- `LEADER`: 12 mappings, including `posts.submit`, but not review or publish.
- `MEMBER`: 10 mappings, including `posts.submit`, but not review or publish.

The canonical permissions include foundational account, role, dashboard,
project, task, and post management permissions, D2 file/profile/member/research
field permissions, post workflow permissions, and own-notification read/mark
read permissions. On a fresh database the mappings are exactly 42. Re-running
`001` is additive: it inserts missing canonical mappings but does not delete
operator-managed role-permission mappings.

## Runtime grants

If the PostgreSQL role `smartlab_user` exists, `001` grants schema `USAGE` and
runtime `SELECT`/`INSERT`/`UPDATE`/`DELETE` only on the SmartLab tables it
creates, plus `USAGE`/`SELECT` only on their sequence-backed IDs. `002` grants
only its `post_reactions` and `post_comments` tables and sequences. The runtime
role is not granted schema or database destructive privileges, and unrelated
objects in `public` are not included.

## Verification workflow

Use a new disposable PostgreSQL database for a migration rehearsal. Apply
`001`, then `002`, re-run both scripts, and inspect the resulting schema, enum
checks, role seed, permission count, role-permission count, and grants. Do not
point a rehearsal at an existing SmartLab database unless it is an explicitly
approved current-lineage upgrade target.
