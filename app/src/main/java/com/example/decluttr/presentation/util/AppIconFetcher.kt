package com.example.decluttr.presentation.util

import android.content.Context
import android.content.pm.PackageManager
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

data class AppIconModel(val packageName: String)

class AppIconFetcher(
    private val context: Context,
    private val data: AppIconModel
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        return try {
            val icon = context.packageManager.getApplicationIcon(data.packageName)
            DrawableResult(
                drawable = icon,
                isSampled = false,
                dataSource = DataSource.DISK
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIconModel> {
        override fun create(data: AppIconModel, options: Options, imageLoader: ImageLoader): Fetcher {
            return AppIconFetcher(context, data)
        }
    }
}
