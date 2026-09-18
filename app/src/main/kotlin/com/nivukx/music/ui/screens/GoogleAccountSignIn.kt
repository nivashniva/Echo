package echo.music.iad1tya.ui.screens

import android.content.Context
import android.content.MutableContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom
import android.util.Base64

internal class GoogleAccountSignIn(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)
    private val credentialContext = MutableContextWrapper(context)

    private fun nonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
        )
    }

    private fun serverClientId(): String? {
        val id = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        return id.takeIf { it != 0 }?.let(context::getString)?.takeIf(String::isNotBlank)
    }

    suspend fun chooseAccount(): Result<GoogleAccountSelection> = runCatching {
        val clientId = serverClientId()
            ?: error("Google Sign-In is not configured: default_web_client_id is missing")

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(clientId)
            .setNonce(nonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val result = credentialManager.getCredential(
            context = credentialContext,
            request = request
        )

        val credential = result.credential
        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            throw IllegalStateException("Google credential could not be parsed", e)
        }

        GoogleAccountSelection(
            email = googleCredential.id,
            displayName = googleCredential.displayName.orEmpty(),
            photoUrl = googleCredential.profilePictureUri?.toString().orEmpty(),
            idToken = googleCredential.idToken
        )
    }
}

data class GoogleAccountSelection(
    val email: String,
    val displayName: String,
    val photoUrl: String,
    val idToken: String,
)
