package com.example.librarysystem.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Standard form field: label sits above the box, the hint lives inside.
 * Enforces maxLength while typing; multi-line fields also cap their height.
 * Scrolls itself back into view whenever the user types (keyboard can cover it after manual scrolling).
 */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    maxLength: Int = 200,
    keyboard: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    required: Boolean = false,
    modifier: Modifier = Modifier
) {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
        Row {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (required) {
                Text(
                    " *",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { new ->
                if (new.length <= maxLength) {
                    onValueChange(new)
                    // Keep the field visible above the keyboard, even after manual scrolling.
                    scope.launch { requester.bringIntoView() }
                }
            },
            placeholder = {
                if (placeholder.isNotBlank()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            maxLines = maxLines,
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        scope.launch { requester.bringIntoView() }
                    }
                }
        )
    }
}
