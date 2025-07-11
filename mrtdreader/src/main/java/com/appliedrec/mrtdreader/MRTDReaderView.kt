package com.appliedrec.mrtdreader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appliedrec.mrtdreader.ui.theme.MRTDReaderTheme

@Composable
internal fun MRTDReaderView(
    resultState: MRTDReaderUiState
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val message = when (resultState) {
            MRTDReaderUiState.Idle -> stringResource(R.string.mrtd_reader_title)
            is MRTDReaderUiState.Reading -> resultState.message
            is MRTDReaderUiState.Warning -> resultState.message
            is MRTDReaderUiState.Finished -> when (resultState.result) {
                is MRTDScanResult.Success -> stringResource(R.string.mrtd_reader_success)
                is MRTDScanResult.Failure -> resultState.result.error.localizedMessage ?: "Unknown error"
                else -> "Cancelled"
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (resultState is MRTDReaderUiState.Reading) {
                LinearProgressIndicator(progress = { resultState.progress.toFloat() })
            }
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MRTDReaderPreview() {
    MRTDReaderTheme {
        MRTDReaderView(resultState = MRTDReaderUiState.Idle)
    }
}