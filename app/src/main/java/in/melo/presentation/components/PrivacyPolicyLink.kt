package `in`.melo.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import `in`.melo.R

@Composable
fun PrivacyPolicyLink() {
    val context = LocalContext.current
    TextButton(onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
    }) {
        Text(stringResource(R.string.privacy_policy_link))
    }
}

internal const val PRIVACY_POLICY_URL = "https://github.com/aarush-io/melo/blob/main/PRIVACY.md"
