package de.djouhri.cockpit.util

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

/** Findet die umschliessende [FragmentActivity] aus einem Compose-Context, sonst null. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
