package io.elephantchess.servicelayer.services.analytics

/**
 * Minimum lifespan (in seconds) between a guest's creation and its last activity for the guest to be
 * counted as a genuine visitor in analytics and online-user metrics.
 *
 * Short-lived guests below this threshold are overwhelmingly scrapers that hit the reference game
 * database once and never come back (single page view, sub-second session). Counting them heavily
 * distorts the "new guests" and "online users" charts, so they are excluded.
 */
const val MIN_GENUINE_GUEST_LIFESPAN_SECONDS = 60
