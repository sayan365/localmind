package ai.localmind.device

import android.content.Context
import android.content.Intent

object WhatsAppHandoff {
    fun createSendIntent(context: Context, message: String): Intent {
        val generic = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val whatsapp = Intent(generic).setPackage("com.whatsapp")
        return if (whatsapp.resolveActivity(context.packageManager) != null) {
            whatsapp
        } else {
            Intent.createChooser(generic, "Send with")
        }
    }
}
