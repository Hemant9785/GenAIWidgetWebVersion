package com.hemant.myapplication

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemant.myapplication.model.RuntimeSnapshot
import com.hemant.myapplication.model.WidgetDocument
import com.hemant.myapplication.pipeline.Orchestrator
import com.hemant.myapplication.ui.GenWidgetSurface
import com.hemant.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WidgetCreatorDashboard(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun WidgetCreatorDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val defaultKey = ""
    var apiKey by remember { mutableStateOf(sharedPref.getString("openai_api_key", "").orEmpty().ifBlank { defaultKey }) }
    var prompt by remember { mutableStateOf("tomorrow will there be rain in Bangalore") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    var generatedDoc by remember { mutableStateOf<WidgetDocument?>(null) }
    var snapshot by remember { mutableStateOf<RuntimeSnapshot?>(null) }
    
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Sleek dark theme
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Text(
            text = "GenUI Forge Client",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp)
        )
        
        Text(
            text = "Enter a prompt to generate and render a native Android widget completely on-device.",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        // Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Developer Credentials",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { 
                        apiKey = it
                        sharedPref.edit().putString("openai_api_key", it).apply()
                    },
                    label = { Text("OpenAI API Key", color = Color(0xFF94A3B8)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF0284C7),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Generator Panel Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Widget Planner Prompt",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Enter prompt (e.g. Weather or Quote)", color = Color(0xFF94A3B8)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF0284C7),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Button(
                    onClick = {
                        if (apiKey.isBlank()) {
                            Toast.makeText(context, "Please enter your OpenAI API key first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (prompt.isBlank()) {
                            Toast.makeText(context, "Please enter a prompt first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        isLoading = true
                        errorMessage = ""
                        generatedDoc = null
                        snapshot = null
                        
                        scope.launch(Dispatchers.IO) {
                            try {
                                // Execute full on-device orchestrator pipeline (Router -> Planner loop -> Resolver -> Layout Gen)
                                 val orchestrator = Orchestrator(apiKey)
                                 val (doc, snap) = orchestrator.generate(prompt)
                                 Log.d("HEMANT_DBG", "MainActivity: Pipeline finished. Widget ID='${doc.id}', Title='${doc.title}', State='${snap.stateKey()}'")
                                 withContext(Dispatchers.Main) {
                                     generatedDoc = doc
                                     snapshot = snap
                                     isLoading = false
                                 }
                            } catch (e: Exception) {
                                Log.e("HEMANT_DBG", "Dashboard query failed", e)
                                withContext(Dispatchers.Main) {
                                    errorMessage = e.message ?: "Failed to generate widget"
                                    isLoading = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Forging Pipeline...")
                    } else {
                        Text("Forge & Render Widget", color = Color.White)
                    }
                }
            }
        }
        
        // Error Display
        if (errorMessage.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFCA5A5),
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }
        }
        
        // Widget Preview Area
        Text(
            text = "Rendered Preview",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier.align(Alignment.Start).padding(top = 8.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            val doc = generatedDoc
            val snap = snapshot
            if (doc != null && snap != null) {
                Log.d("HEMANT_DBG", "MainActivity: doc and snap are non-null. Composing GenWidgetSurface.")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    GenWidgetSurface(
                        document = doc,
                        surfaceId = "surface.ready.4x3",
                        runtimeSnapshot = snap,
                        onAction = { event ->
                            Toast.makeText(context, "Triggered Action: ${event.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } else {
                Log.d("HEMANT_DBG", "MainActivity: doc or snap is null. Showing empty state. docIsNull=${doc == null}, snapIsNull=${snap == null}")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No widget loaded", color = Color(0xFF64748B), fontSize = 14.sp)
                    Text("Submit a prompt above to render here", color = Color(0xFF475569), fontSize = 12.sp)
                }
            }
        }
    }
}
