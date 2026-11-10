package com.toxicorca.slideremote.demo

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.toxicorca.slideremote.app

/**
 * Debug builds only. Enables demo mode when the app is launched with
 *
 *     adb shell am start -n com.toxicorca.slideremote/.MainActivity --ez demo true
 *
 * A ContentProvider is used purely as an early startup hook, so main code never
 * references anything in this source set.
 */
class DemoInstaller : ContentProvider() {

    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return false
        application.registerActivityLifecycleCallbacks(object : SimpleLifecycleCallbacks() {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity.intent?.getBooleanExtra(EXTRA_DEMO, false) == true) {
                    DemoSeeder.start(activity.app)
                }
            }
        })
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    private companion object {
        const val EXTRA_DEMO = "demo"
    }
}

private open class SimpleLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
