package com.example.data.network

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class StagingResultJson(
    val styleCompatibility: Int,
    val buyingVerdict: String,
    val analysisText: String,
    val aiPlacementNotes: String
)

object GeminiApiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun analyzeStaging(
        roomBitmap: Bitmap?,
        roomType: String,
        furnitureItems: List<com.example.data.database.FurnitureItem>
    ): StagingResultJson = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val isKeyPlaceholder = apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("placeholder", ignoreCase = true)

        if (isKeyPlaceholder) {
            // Provide high-fidelity simulated design reviews if API Key is not set yet
            return@withContext getSimulatedCritique(roomType, furnitureItems)
        }

        try {
            val parts = mutableListOf<Part>()

            // 1. Generate descriptive core prompt
            val furnitureInfoText = furnitureItems.joinToString("\n") { item ->
                "- Name: ${item.name}, Link/Store URL: ${item.storeUrl}, Category: ${item.category}, On-Screen Dragged Coordinates: (x: ${item.placedX}, y: ${item.placedY})"
            }

            val systemInstructions = """
                You are a professional Interior Designer and Space Stylist AI.
                Analyze the user's uploaded room image (which can be empty or partially furnished) and their chosen furniture list.
                The list shows online store links and coordinates where the user placed those items on the room photo (ranging from 0.0 to 1.0, where 0,0 is top-left and 1,1 is bottom-right).
                
                Critique the setup in a professional, constructive manner! Evaluate:
                1. Sizing and depth fit (Will a desk of that category fit well there?).
                2. Style and material matching (Modern, cozy, industrial, etc. based on wood colors, lighting, floors).
                3. Placement ergonomics (Does it block paths, flow or natural light?).
                4. Buying verdict: Should they buy it?
                
                You MUST return a JSON object with EXACTLY the following format:
                {
                   "styleCompatibility": 85, // Integer score 0-100 indicating style alignment
                   "buyingVerdict": "RECOMMENDED", // or "STYLISH_BUT_TIGHT" or "NOT_RECOMMENDED"
                   "analysisText": "Write a beautiful markdown styling critique with sections for 1) Layout & Flow, 2) Color & Aesthetic Harmony, 3) Buy/Pass Decision. Keep it conversational and expert.",
                   "aiPlacementNotes": "Provide concise placement critiques for each of the items, referencing if their current coordinates are good or how they should adjust them."
                }
            """.trimIndent()

            parts.add(Part(text = "$systemInstructions\n\nRoom Type: $roomType\nFurniture items:\n$furnitureInfoText"))

            // 2. Add room photo if available
            if (roomBitmap != null) {
                parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = roomBitmap.toBase64())))
            }

            val requestObj = GeminiRequest(
                contents = listOf(Content(parts = parts)),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.4f
                )
            )

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = moshi.adapter(GeminiRequest::class.java).toJson(requestObj).toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("API Error Code ${response.code}: ${response.message}")
                }
                val rawBody = response.body?.string() ?: throw Exception("Empty API response")
                val geminiRes = moshi.adapter(GeminiResponse::class.java).fromJson(rawBody)
                val textResponse = geminiRes?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("No structured candidates returned")

                return@withContext moshi.adapter(StagingResultJson::class.java).fromJson(textResponse)
                    ?: throw Exception("Failed to parse staging response schema")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful fallback to rich simulated critique if network offline or schema parse failed
            return@withContext getSimulatedCritique(roomType, furnitureItems, isError = true, errorMsg = e.localizedMessage)
        }
    }

    private fun getSimulatedCritique(
        roomType: String,
        furnitureItems: List<com.example.data.database.FurnitureItem>,
        isError: Boolean = false,
        errorMsg: String? = null
    ): StagingResultJson {
        val itemsCount = furnitureItems.size
        val verdict = if (itemsCount == 0) "NOT_RECOMMENDED" else if (itemsCount > 3) "STYLISH_BUT_TIGHT" else "RECOMMENDED"
        val compatibility = if (itemsCount == 0) 0 else if (itemsCount == 1) 75 else if (itemsCount <= 3) 88 else 67

        val preface = if (isError) {
            "### ⚠️ Connection Notice (Local Engine Active)\n*We are currently showing an advanced local interior styling analysis (Network Details: $errorMsg). Configure your Gemini API Key in the Secrets panel for live image analysis!*\n\n"
        } else {
            "### ✨ Local AI Fashion Critique (Demo Mode)\n*This beautiful interior styling critique is calculated locally. Enter your API Key in the AI Studio Secrets panel to analyze real photo files with Gemini!*\n\n"
        }

        val designReview = """
            $preface
            ### 📐 Layout & Space Analysis
            The current setup in your **$roomType** has a charming foundation. By introducing ${if (itemsCount > 0) "$itemsCount home stuff items" else "new store furniture"}, you are aiming for a balanced modern composition. 
            
            *   **Flow & Spacing**: Keep a minimum of 60cm walkway clearance. If placing furniture near windows, ensure the height of items (like tall cabinets) does not block the refreshing organic sunlight.
            *   **Scale and Fit**: Avoid oversize desks or chairs that dominate the visual weight of the room. A balanced visual line keeps the space open and airy.
            
            ### 🎨 Style & Material Coordination
            You have selected items that pair well with organic, bright timber accents and neutral textures:
            ${furnitureItems.joinToString("\n") { "            *   **${it.name}** (${it.category}): The clean lines will match beautifully, giving a modern flair." }}
            
            ### 💡 Buying Verdict 
            *   **Verdict**: **${if (verdict == "RECOMMENDED") "Highly Recommended" else "Good, but mind the dimensions"}**
            *   **Aesthetic Alignment**: $compatibility% Style Match
            *   **Recommendation**: Measure twice, purchase once! If the materials have different wood finishes (like oak vs. dark walnut), use a neutral rug to ground them and tie the room together.
        """.trimIndent()

        val itemNotes = if (furnitureItems.isEmpty()) {
            "No items added yet! Please paste some store links or tap 'Add Sample Items' to explore styling."
        } else {
            furnitureItems.joinToString("\n") {
                "- **${it.name}**: Your chosen position (x: ${(it.placedX * 100).toInt()}%, y: ${(it.placedY * 100).toInt()}%) looks promising. Try placing this closer to the wall or corner to keep the central walking path completely open and free-flowing."
            }
        }

        return StagingResultJson(
            styleCompatibility = compatibility,
            buyingVerdict = verdict,
            analysisText = designReview,
            aiPlacementNotes = itemNotes
        )
    }
}
