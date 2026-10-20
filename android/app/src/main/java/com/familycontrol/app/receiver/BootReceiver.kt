package com.familycontrol.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familycontrol.app.MainActivity

/**
 * Odbiornik rozruchowy uruchamiany po restarcie tabletu (BOOT_COMPLETED).
 * Zapewnia, że launcher podnosi się automatycznie jako domyślny ekran główny.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
        }
    }
}
