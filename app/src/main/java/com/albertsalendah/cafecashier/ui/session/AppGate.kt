package com.albertsalendah.cafecashier.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albertsalendah.cafecashier.CafeCashierApp
import com.albertsalendah.cafecashier.domain.Access
import com.albertsalendah.cafecashier.domain.UserRole
import com.albertsalendah.cafecashier.domain.effectiveRole
import com.albertsalendah.cafecashier.ui.lock.LockScreen
import com.albertsalendah.cafecashier.ui.navigation.AppNav

// The door of the app: the lock screen while staff accounts are on and nobody is signed in, the app
// otherwise. The app (with its back stack and screen state) is rebuilt whenever the role changes, and
// is left completely while locked, so the next user never inherits an open screen or a tab.
@Composable
fun AppGate() {
    val session = (LocalContext.current.applicationContext as CafeCashierApp).session
    val access by remember { session.access }.collectAsStateWithLifecycle(initialValue = null)

    when (val current = access) {
        // Still reading the settings and users: show nothing rather than flash the app.
        null -> Unit

        Access.Locked -> {
            // Forget a stale sign-in, so a user who was deactivated and later reactivated is not let back in.
            LaunchedEffect(Unit) { session.signOut() }
            LockScreen()
        }

        // Open (accounts off) and SignedIn share this branch, so turning accounts on and the first
        // Admin staying signed in does not restart the app. The key is the role, not the person: a
        // change of person always passes through the lock screen.
        else -> key(current.effectiveRole() ?: UserRole.ADMIN) {
            AppNav(
                currentUser = (current as? Access.SignedIn)?.user,
                onLock = session::signOut
            )
        }
    }
}
