package io.elephantchess.model

enum class UserType {

    AUTHENTICATED,
    GUEST,

    /**
     * A residual, identity-less user that only holds the game/puzzle/rating data transferred from a
     * deleted account. It has no email, username or password and can never authenticate or hold a
     * session. See the account-deletion flow ([io.elephantchess.db.services.UserDaoService.deleteAndAnonymize]).
     */
    ANONYMIZED,
}
