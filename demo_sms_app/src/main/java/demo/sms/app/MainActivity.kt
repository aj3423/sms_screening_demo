package demo.sms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import demo.sms.app.ui.theme.SMSScreeningDemoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SMSScreeningDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SmsSimulatorScreen(
                        screeningClient = PublicSmsScreeningClient(applicationContext),
                        modifier = Modifier
                            .padding(innerPadding)
                            .statusBarsPadding()
                    )
                }
            }
        }
    }
}

private enum class SimSlot(val label: String, val value: Int) {
    SIM_1("SIM 1", 1),
    SIM_2("SIM 2", 2),
}

@Composable
private fun SmsSimulatorScreen(
    screeningClient: PublicSmsScreeningClient,
    modifier: Modifier = Modifier,
) {
    var number by remember { mutableStateOf("") }
    var smsContent by remember { mutableStateOf("") }
    var simSlot by remember { mutableStateOf(SimSlot.SIM_1) }
    var queryResult by remember { mutableStateOf<ScreeningQueryResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Demo SMS App",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Simulate a new SMS and query a public screening provider through bindService() + Messenger.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Number") },
            singleLine = true,
        )
        OutlinedTextField(
            value = smsContent,
            onValueChange = { smsContent = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("SMS Content") },
            minLines = 4,
        )
        Text(
            text = "SIM Slot",
            style = MaterialTheme.typography.titleMedium,
        )
        SimSlot.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = simSlot == option,
                        onClick = { simSlot = option },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = simSlot == option,
                    onClick = null,
                )
                Text(
                    text = option.label,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    queryResult = screeningClient.shouldBlock(
                        number = number,
                        smsContent = smsContent,
                        simSlot = simSlot.value,
                    )
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        ) {
            Text("Simulate a new SMS")
        }
        when {
            isLoading -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("Querying screening provider...")
                }
            }

            queryResult != null -> {
                ScreeningResultCard(
                    result = queryResult!!,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ScreeningResultCard(
    result: ScreeningQueryResult,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (result) {
                is ScreeningQueryResult.Failure -> {
                    Text(
                        text = "Query failed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(result.message)
                }

                is ScreeningQueryResult.Success -> {
                    Text(
                        text = "Provider: ${result.providerLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (result.blocked) "Blocked = true" else "Blocked = false",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
