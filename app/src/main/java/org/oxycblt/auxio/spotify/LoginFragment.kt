package org.oxycblt.auxio.spotify

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.oxycblt.auxio.sync.SyncWorker

class LoginFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val frame = FrameLayout(requireContext())

        val webView = WebView(requireContext()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                removeAllCookies(null)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url?.contains("open.spotify.com") == true) {
                        val cookieStr = CookieManager.getInstance().getCookie(url) ?: return
                        val cookies = parseCookieString(cookieStr)
                        if (cookies.containsKey("sp_dc")) {
                            SpotifySession.onLoginComplete(requireContext(), cookies)
                            SyncWorker.schedule(requireContext())
                            findNavController().navigateUp()
                        }
                    }
                }
            }

            loadUrl("https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com")
        }

        frame.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        return frame
    }
}
