package pe.edu.upn.llmia.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import pe.edu.upn.llmia.BuildConfig

/**
 * Inicio de sesión del operador con Google, en el mismo proyecto Firebase que el panel web.
 * El backend verifica el token de Firebase y que el UID esté en LLMIA_ALLOWED_UIDS.
 * La configuración viene de local.properties (no se versiona). Sin ella, la app sigue
 * funcionando para manejar el robot; el puente y las preguntas requieren sesión.
 */
class OperatorAuth(private val context: Context) {
    val configured: Boolean = listOf(
        BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_APP_ID,
        BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_WEB_CLIENT_ID,
    ).all { it.isNotBlank() }

    private val auth: FirebaseAuth? by lazy {
        if (!configured) return@lazy null
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(),
                APP_NAME,
            )
        FirebaseAuth.getInstance(app)
    }

    val displayName: String?
        get() = auth?.currentUser?.let { it.displayName ?: it.email ?: "Operador" }

    suspend fun signIn(activity: Activity): String {
        val firebase = auth ?: throw IllegalStateException("Falta la configuración de Firebase en local.properties")
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.FIREBASE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) throw IllegalStateException("Credencial de Google no reconocida")
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        firebase.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
        return displayName ?: "Operador"
    }

    suspend fun idToken(): String {
        val user = auth?.currentUser ?: throw IllegalStateException("Inicia sesión como operador")
        return user.getIdToken(false).await().token ?: throw IllegalStateException("Sin token de operador")
    }

    fun signOut() {
        auth?.signOut()
    }

    private companion object {
        const val APP_NAME = "llmia"
    }
}

