package com.fieldpin.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldpin.ui.components.FpTextField
import com.fieldpin.ui.components.PrimaryButton
import com.fieldpin.ui.theme.Green400
import com.fieldpin.ui.theme.Teal400
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(vm: LoginViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val glow = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(Brush.verticalGradient(listOf(glow.copy(alpha = 0.18f), MaterialTheme.colorScheme.background), endY = 900f))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Teal400,
                modifier = Modifier.size(76.dp),
            ) {
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Teal400, Green400))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Place, null, tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("FieldPin", style = MaterialTheme.typography.displaySmall)
            Text("Site inspection", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(36.dp))

            FpTextField(
                value = state.email,
                onValueChange = vm::onEmail,
                label = "Email",
                leadingIcon = Icons.Filled.Email,
                error = state.emailError,
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(8.dp))
            FpTextField(
                value = state.password,
                onValueChange = vm::onPassword,
                label = "Password",
                leadingIcon = Icons.Filled.Lock,
                error = state.passwordError,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = vm::toggleShowPassword) {
                        Icon(if (state.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password visibility")
                    }
                },
            )
            if (state.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Sign in", onClick = vm::submit, loading = state.loading, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Text(
                "You stay signed in after restarting the app.\nWorks offline once signed in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
