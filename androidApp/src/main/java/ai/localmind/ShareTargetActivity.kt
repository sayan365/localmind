package ai.localmind

import ai.localmind.device.SharedContentStore
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable

class ShareTargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importSharedContent(intent)
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_SHARED_CONTENT_ADDED, true)
        })
        finish()
    }

    private fun importSharedContent(source: Intent) {
        val store = SharedContentStore(this)
        val text = source.getStringExtra(Intent.EXTRA_TEXT)
        when (source.action) {
            Intent.ACTION_SEND -> {
                val uri = source.parcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (!text.isNullOrBlank() || uri != null) store.add(source.type, text, uri?.toString())
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = source.parcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                if (!text.isNullOrBlank() && uris.isEmpty()) store.add(source.type, text, null)
                uris.forEach { uri -> store.add(source.type, text, uri.toString()) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : Parcelable> Intent.parcelableExtra(key: String): T? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else getParcelableExtra(key)

    @Suppress("DEPRECATION")
    private inline fun <reified T : Parcelable> Intent.parcelableArrayListExtra(key: String): ArrayList<T>? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(key, T::class.java) else getParcelableArrayListExtra(key)
}
