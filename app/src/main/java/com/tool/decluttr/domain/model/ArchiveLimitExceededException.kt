package com.tool.decluttr.domain.model

class ArchiveLimitExceededException(
    val used: Int,
    val limit: Int,
    val requested: Int,
    val overflow: Int
) : IllegalStateException(
    "Archive limit exceeded: used=$used limit=$limit requested=$requested overflow=$overflow"
)
