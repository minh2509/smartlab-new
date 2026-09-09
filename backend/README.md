# SmartLab backend database bootstrap

PostgreSQL is the canonical SmartLab database. Hibernate runs with
`ddl-auto=none`, so it does not create, update, or own the schema.

Run the SQL files in this order against a new, explicitly selected PostgreSQL
database:

1. `sql/001_smartlab_postgres_schema_seed.sql`
2. `sql/002_social_feed_interactions.sql`
3. `sql/003_d3_batch2_events.sql`
4. `sql/004_project_join_requests.sql`
5. `sql/005_landing_page_data_contracts.sql`
6. `sql/006_lab_news_articles.sql`
7. `sql/007_lab_articles.sql`
8. `sql/008_research_field_cover_image.sql`
9. `sql/009_achievement_editor_v2.sql`
10. `sql/010_research_publications_to_lab_achievements.sql`
11. `sql/011_achievement_editor_v2_repair.sql`
12. `sql/012_gallery_system.sql`

These are manual, operator-executed SQL migrations. The repository does not
contain Flyway, Liquibase, or another automatic numbered migration runner.
Operators must inspect the target database lineage and each migration's
prerequisites instead of blindly executing every numbered file.

Use your normal secure PostgreSQL credential mechanism; do not place database
passwords in shell history, command output, tickets, or documentation.

## Fresh bootstrap and current-lineage upgrades

On an empty database, apply `001` through `011` in numeric order. `001` creates
the current backend schema, roles, permissions, and foundational seed data;
`002` adds the social-feed reaction and comment tables. Both scripts are
designed to be re-run safely for the current `001`/`002` lineage.

The later migrations extend that foundation:

- `003` adds the event-management schema.
- `004` adds the project join-request lifecycle.
- `005` adds project recruitment and the historical
  `research_publications` contract used by migration `010`.
- `006` adds lab news articles.
- `007` adds official lab articles and their published/draft contract.
- `008` adds research-field cover-file references.
- `009` creates the achievement editor v2 schema, including achievement file
  mappings.
- `010` migrates historical `research_publications` rows into
  `lab_achievements` while retaining the source table for rollback and audit.
- `011` verifies and repairs partial achievement editor v2 installations.
- `012` creates the Gallery item ownership/publication contract, the
  `GALLERY_MANAGE` permission, and the ADMIN mapping. It creates no gallery
  rows or sample files. Apply it only after a verified backup and target
  lineage check; deploy the backend schema before enabling Gallery UI.

### Migration 010 prerequisite and lineage decision

Before executing `010`, verify that the target database contains the
historical `public.research_publications` relation expected from migration
`005`. If it exists, execute `010` after `009` as part of the normal ordered
procedure. The migration refuses conflicting pre-existing achievement IDs and
keeps the historical source relation after copying its rows.

If `public.research_publications` is absent, do not create a synthetic source
table merely to satisfy `010`, and do not run `010` blindly. Stop and classify
the target database lineage before proceeding. A database may already have a
separately provisioned `lab_achievements` schema that requires repair rather
than historical-publication migration.

The current local SmartLab verification database is one such non-production
lineage: `research_publications` is absent, a synthetic `lab_achievements`
table already existed, `010` was intentionally not run, and `011` was applied
as the schema repair. This local decision must not be generalized to other
deployment targets.

### Migration 011 repair role

`011` is an additive, idempotent, forward repair for a partially provisioned
achievement editor v2 schema. It provisions or verifies
`recognizing_organization`, the achievement attachment table, its sequence,
required indexes, constraints, and runtime grants. It does not rewrite
achievement rows and does not replace `009` or `010` on a fresh installation.
In a complete fresh `001` through `011` chain, its schema additions should
already exist and the repair operations should effectively be no-ops while
the final assertions still verify compatibility.

### Deployment safety

Before any schema write:

1. Identify and explicitly select the target database.
2. Take and verify a recoverable backup.
3. Inspect the target lineage and verify every migration prerequisite.
4. Execute each approved file with transaction-aware tooling and stop-on-error
   behavior, such as `psql --set=ON_ERROR_STOP=1`, where applicable.
5. Verify the resulting schema, grants, application startup, and affected
   endpoint health before completing deployment.

Use generic connection placeholders and the normal secure credential
mechanism. Never place passwords, tokens, private hosts, or real connection
strings in commands committed to the repository.

## Release toolchain

- Frontend: Node.js `>=22.12 <23` for the supported Node 22 release line.
- Backend: JDK 21, matching the Maven `java.version` configuration.

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

Use a new disposable PostgreSQL database for a migration rehearsal. Apply the
approved `001` through `011` fresh-install sequence, then inspect the resulting
schema, enum checks, role seed, permission count, role-permission count, grants,
and affected endpoint health. Re-run only migrations whose source explicitly
documents idempotent behavior. Do not point a rehearsal at an existing
SmartLab database unless it is an explicitly approved and classified upgrade
target.
