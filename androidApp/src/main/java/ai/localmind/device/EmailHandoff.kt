package ai.localmind.device

import android.content.Context
import android.content.Intent
import android.net.Uri

object EmailHandoff {
    fun createDraftIntent(context: Context, address: String, message: String): Intent {
        val generic = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)).apply {
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val gmail = Intent(generic).setPackage("com.google.android.gm")
        return if (gmail.resolveActivity(context.packageManager) != null) gmail else generic
    }
}
