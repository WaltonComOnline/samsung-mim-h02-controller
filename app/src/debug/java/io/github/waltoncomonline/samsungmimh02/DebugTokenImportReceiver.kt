package io.github.waltoncomonline.samsungmimh02

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.waltoncomonline.samsungmimh02.data.PendingTokenImport

class DebugTokenImportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val host = intent.getStringExtra(EXTRA_HOST) ?: return
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        try {
            PendingTokenImport.stage(context, host, token)
        } finally {
            intent.removeExtra(EXTRA_HOST)
            intent.removeExtra(EXTRA_TOKEN)
        }
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launchIntent)
        }
    }

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_TOKEN = "token"
    }
}
