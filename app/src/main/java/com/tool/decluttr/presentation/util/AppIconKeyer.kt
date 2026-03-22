package com.tool.decluttr.presentation.util

import coil.key.Keyer
import coil.request.Options

class AppIconKeyer : Keyer<AppIconModel> {
    override fun key(data: AppIconModel, options: Options): String {
        return data.packageName
    }
}
