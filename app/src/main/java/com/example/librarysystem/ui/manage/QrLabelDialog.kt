package com.example.librarysystem.ui.manage

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.librarysystem.util.QrCodes
import com.example.librarysystem.util.QrExport

/**
 * Big QR preview with Share / Save. Shared by the copy list and (later) the
 * label cart so both show the same thing.
 */
@Composable
fun QrLabelDialog(
    title: String,
    subtitle: String,
    isbn: String?,
    copyNo: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val payload = remember(isbn, copyNo) { QrCodes.payload(isbn, copyNo) }
    val bitmap = remember(payload) { QrCodes.bitmap(payload) }
    val safeName = "label-${copyNo.ifBlank { "copy" }}"

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR label",
                    modifier = Modifier
                        .size(232.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(8.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    payload,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { QrExport.share(context, bitmap, safeName) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Share") }
                    Button(
                        onClick = {
                            val uri = QrExport.saveToGallery(context, bitmap, safeName)
                            Toast.makeText(
                                context,
                                if (uri != null) "Saved to Pictures/SmartLibrary"
                                else "Could not save the label",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}
