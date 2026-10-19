package io.elephantchess.servicelayer.dto.admin

/**
 * Response for the "recently online" (e.g. last 24h) admin overview widget.
 *
 * Only authenticated users are listed by name; guests are represented by their count only, since
 * their names are not displayed and fetching them all is expensive (especially with scrapers
 * creating many guest users).
 */
data class RecentlyOnlineUsersResponse(
    val authenticatedUsers: List<Entry>,
    val guestCount: Int,
) {

    data class Entry(
        val id: String,
        val username: String,
    )

}
