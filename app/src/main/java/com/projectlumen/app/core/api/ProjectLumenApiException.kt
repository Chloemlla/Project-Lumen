package com.projectlumen.app.core.api

import java.io.IOException

class ProjectLumenApiException(
    val statusCode: Int,
    message: String,
) : IOException(message)
