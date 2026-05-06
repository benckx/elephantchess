package io.elephantchess.servicelayer.dto

data class AutocompleteResponse(val entries: List<Entry>) {

    data class Entry(
        val id: String,
        val name: String
    )

}
