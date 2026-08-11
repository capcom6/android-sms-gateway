package me.capcom.smsgateway.modules.localserver.domain.messages

/**
 * Sort order for GET /messages, mirroring the Go server contract.
 */
enum class MessageSort {
    CreatedAtAsc,
    CreatedAtDesc;

    fun toDomain(): me.capcom.smsgateway.modules.messages.data.MessageSort = when (this) {
        CreatedAtAsc -> me.capcom.smsgateway.modules.messages.data.MessageSort.CreatedAtAsc
        CreatedAtDesc -> me.capcom.smsgateway.modules.messages.data.MessageSort.CreatedAtDesc
    }

    companion object {
        /**
         * Parse the `sort` query parameter value.
         * null/absent -> CreatedAtDesc; "created_at" -> CreatedAtAsc;
         * "-created_at" -> CreatedAtDesc; anything else -> IllegalArgumentException.
         */
        fun parse(raw: String?): MessageSort = when (raw) {
            null -> CreatedAtDesc
            "created_at" -> CreatedAtAsc
            "-created_at" -> CreatedAtDesc
            else -> throw IllegalArgumentException("sort must be one of: created_at, -created_at")
        }
    }
}
