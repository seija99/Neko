package eu.kanade.tachiyomi.network

import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.source.online.MangaDexLoginHelper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(val loginHelper: MangaDexLoginHelper) :
    Authenticator {

    private val lock = Semaphore(1)

    override fun authenticate(route: Route?, response: Response): Request? {
        XLog.i("Detected Auth error ${response.code} on ${response.request.url}")
        val token = refreshToken(loginHelper)
        return if (token.isEmpty()) {
            null
        } else {
            response.request.newBuilder().header("Authorization", token).build()
        }
    }

    fun refreshToken(loginHelper: MangaDexLoginHelper): String {
        var validated = false

        runBlocking {
            lock.acquire()
            val checkToken =
                loginHelper.isAuthenticated()
            if (checkToken) {
                XLog.i("Token is valid, other thread must have refreshed it")
                validated = true
            }
            if (validated.not()) {
                XLog.i("Token is invalid trying to refresh")
                validated =
                    loginHelper.refreshToken()
            }

            if (validated.not()) {
                XLog.i("Did not refresh token, trying to login")
                validated = loginHelper.login()
            }
            lock.release()
        }
        return when {
            validated -> "Bearer ${loginHelper.preferences.sessionToken()!!}"
            else -> ""
        }
    }
}
