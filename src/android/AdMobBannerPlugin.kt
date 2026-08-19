
package com.admob.banner

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONObject

class AdMobBannerPlugin : CordovaPlugin() {

    private var bannerContainer: FrameLayout? = null
    private var adView: AdView? = null
    private var currentAdUnitId: String? = null

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        when (action) {
            "create" -> {
                val adUnitId = args.optString(0, "")
                if (adUnitId.isEmpty()) {
                    callbackContext.error("adUnitId is required")
                    return true
                }
                currentAdUnitId = adUnitId
                cordova.activity.runOnUiThread {
                    createBanner(adUnitId, callbackContext)
                }
                return true
            }
            "destroy" -> {
                currentAdUnitId = null
                cordova.activity.runOnUiThread {
                    destroyBanner(callbackContext)
                }
                return true
            }
            else -> return false
        }
    }

    private fun createBanner(adUnitId: String, callbackContext: CallbackContext?) {
        val activity = cordova.activity ?: run {
            callbackContext?.error("Activity is unavailable")
            return
        }

        removeBannerViews()

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val container = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        val newAdView = AdView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL
            )
        }

        container.addView(newAdView)
        rootView.addView(container)

        bannerContainer = container
        adView = newAdView

        // Safe area and software keyboard (IME) visibility listener
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeVisible) {
                container.visibility = View.GONE
            } else {
                container.visibility = View.VISIBLE
                val safeAreaInsets = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                (container.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    lp.bottomMargin = safeAreaInsets.bottom
                    container.layoutParams = lp
                }
            }
            insets
        }

        loadBannerAd(adUnitId, newAdView)
        callbackContext?.success()
    }

    private fun loadBannerAd(adUnitId: String, view: AdView) {
        val activity = cordova.activity ?: return
        val adWidth = getAdWidthInDp()
        val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, adWidth)
        val bannerRequest = BannerAdRequest.Builder(adUnitId, adSize).build()

        view.loadAd(bannerRequest, object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(bannerAd: BannerAd) {
                bannerAd.adEventCallback = object : BannerAdEventCallback() {}

                val loadedSize = bannerAd.adSize
                val sizeJson = JSONObject().apply {
                    put("width", loadedSize.width)
                    put("height", loadedSize.height)
                }

                emitJsEvent("admob.ad.load", null)
                emitJsEvent("admob.ad.resize", sizeJson.toString())
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                val escapedError = JSONObject.quote(loadAdError.message)
                emitJsEvent("admob.ad.loadfail", escapedError)
            }
        })
    }

    // Handles orientation changes
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val adUnitId = currentAdUnitId ?: return

        cordova.activity?.let { activity ->
            activity.runOnUiThread {
                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                // Post to ensure view bounds and metrics update after rotation
                rootView?.post {
                    adView?.let { view ->
                        loadBannerAd(adUnitId, view)
                    } ?: run {
                        createBanner(adUnitId, null)
                    }
                }
            }
        }
    }

    private fun destroyBanner(callbackContext: CallbackContext) {
        currentAdUnitId = null
        removeBannerViews()
        callbackContext.success()
    }

    private fun removeBannerViews() {
        adView?.let {
            it.destroy()
            adView = null
        }
        bannerContainer?.let { container ->
            (container.parent as? ViewGroup)?.removeView(container)
            bannerContainer = null
        }
    }

    private fun getAdWidthInDp(): Int {
        val activity = cordova.activity ?: return 360
        val displayMetrics: DisplayMetrics = activity.resources.displayMetrics
        val widthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            displayMetrics.widthPixels
        }
        return (widthPixels / displayMetrics.density).toInt()
    }

    private fun emitJsEvent(eventName: String, detailJsonOrLiteral: String?) {
        cordova.activity?.runOnUiThread {
            val js = if (detailJsonOrLiteral != null) {
                "window.admob && window.admob._emitEvent('$eventName', $detailJsonOrLiteral);"
            } else {
                "window.admob && window.admob._emitEvent('$eventName');"
            }
            webView.engine.evaluateJavascript(js, null)
        }
    }

    override fun onDestroy() {
        currentAdUnitId = null
        removeBannerViews()
        super.onDestroy()
    }
}
