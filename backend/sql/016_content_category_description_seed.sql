-- Add the description expected by ContentCategoryEntity and repair the
-- canonical category seed. This migration is safe to run more than once.

BEGIN;

ALTER TABLE public.content_categories
    ADD COLUMN IF NOT EXISTS description VARCHAR(500);

INSERT INTO public.content_categories (code, name, description, is_active)
VALUES
    ('NEWS', 'News', 'News and updates from Smart Lab', TRUE),
    ('RESEARCH', 'Research', 'Research results, publications, and discoveries', TRUE),
    ('EVENT', 'Event', 'Seminars, workshops, and community events', TRUE),
    ('ANNOUNCEMENT', 'Announcement', 'Official announcements from Smart Lab', TRUE)
ON CONFLICT (code) DO UPDATE
SET description = COALESCE(public.content_categories.description, EXCLUDED.description);

COMMIT;
