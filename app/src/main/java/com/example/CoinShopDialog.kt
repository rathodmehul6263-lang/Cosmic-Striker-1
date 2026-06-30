package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.delay
import kotlin.random.Random

// Data model for displaying pack features before/after billing setup
data class CoinPack(
    val id: String,
    val name: String,
    val coinAmount: Int,
    val defaultPriceLabel: String,
    val description: String,
    val highlight: String? = null,
    val gradientColors: List<Color>,
    val emoji: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinShopDialog(
    isBillingReady: Boolean,
    productDetailsMap: Map<String, ProductDetails>,
    onBuyPack: (productId: String) -> Unit,
    onClose: () -> Unit,
    playClick: () -> Unit,
    showSuccessOverlay: Boolean,
    successGrantedAmount: Int,
    onSuccessOverlayDismiss: () -> Unit
) {
    val coinPacks = remember {
        listOf(
            CoinPack(
                id = BillingManager.STARTER_PACK,
                name = "Starter Pack",
                coinAmount = 2000,
                defaultPriceLabel = "₹29",
                description = "Get a cosmic head start",
                highlight = "GOOD START",
                gradientColors = listOf(Color(0xFFFF8C00), Color(0xFFFF4500)),
                emoji = "🪙"
            ),
            CoinPack(
                id = BillingManager.VALUE_PACK,
                name = "Value Pack",
                coinAmount = 10000,
                defaultPriceLabel = "₹99",
                description = "Perfect booster for space warfare",
                highlight = "POPULAR",
                gradientColors = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)),
                emoji = "💰"
            ),
            CoinPack(
                id = BillingManager.PRO_PACK,
                name = "Pro Pack",
                coinAmount = 25000,
                defaultPriceLabel = "₹199",
                description = "Unlock premium upgrades and ships",
                highlight = "RECOMMENDED",
                gradientColors = listOf(Color(0xFF8A2387), Color(0xFFE94057)),
                emoji = "💎"
            ),
            CoinPack(
                id = BillingManager.MEGA_PACK,
                name = "Mega Pack",
                coinAmount = 70000,
                defaultPriceLabel = "₹499",
                description = "Dominate the leaderboards!",
                highlight = "150% VALUE",
                gradientColors = listOf(Color(0xFFF12711), Color(0xFFF5AF19)),
                emoji = "🏆"
            ),
            CoinPack(
                id = BillingManager.GALAXY_PACK,
                name = "Galaxy Pack",
                coinAmount = 150000,
                defaultPriceLabel = "₹999",
                description = "Ultimate treasury of the empire",
                highlight = "BEST VALUE",
                gradientColors = listOf(Color(0xFF7F00FF), Color(0xFFFF007F)),
                emoji = "🌌"
            )
        )
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE03030D)),
            contentAlignment = Alignment.Center
        ) {
            // Main Content Box
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f)
                    .background(Color(0xFC0A102A), shape = RoundedCornerShape(24.dp))
                    .border(
                        BorderStroke(
                            2.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF00F0FF),
                                    Color(0xFFFF00A0),
                                    Color(0xFF00F0FF)
                                )
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Coin Shop",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "COSMIC SHOP",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            color = Color.White,
                            letterSpacing = 1.5.sp
                        )
                    }

                    // Close Button
                    IconButton(
                        onClick = {
                            playClick()
                            onClose()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x33FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Shop",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "Refuel your galaxy fleet with coin packs. Real-time secure purchases handled via Google Play.",
                    color = Color(0xAAFFFFFF),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )

                // Subtitle/Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1F00F0FF), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PLAY BILLING STATUS:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00F0FF),
                        fontFamily = FontFamily.Monospace
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isBillingReady) Color(0xFF00FF88) else Color(0xFFFF2200),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBillingReady) "CONNECTED" else "CONNECTING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBillingReady) Color(0xFF00FF88) else Color(0xFFFF2200),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Item List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(coinPacks) { pack ->
                        val playStoreDetails = productDetailsMap[pack.id]
                        // Determine Price string from Google Play if available
                        val priceText = if (playStoreDetails != null) {
                            val offers = playStoreDetails.oneTimePurchaseOfferDetails
                            offers?.formattedPrice ?: pack.defaultPriceLabel
                        } else {
                            pack.defaultPriceLabel
                        }

                        CoinPackRow(
                            pack = pack,
                            displayPrice = priceText,
                            onPurchaseClick = {
                                playClick()
                                onBuyPack(pack.id)
                            }
                        )
                    }
                }
            }

            // SUCCESS OVERLAY CELEBRATION
            AnimatedVisibility(
                visible = showSuccessOverlay,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut()
            ) {
                PurchaseSuccessOverlay(
                    grantedAmount = successGrantedAmount,
                    onDismiss = onSuccessOverlayDismiss
                )
            }
        }
    }
}

@Composable
fun CoinPackRow(
    pack: CoinPack,
    displayPrice: String,
    onPurchaseClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1435).copy(alpha = 0.7f), shape = RoundedCornerShape(16.dp))
            .border(
                BorderStroke(
                    1.2.dp,
                    Brush.linearGradient(
                        listOf(
                            pack.gradientColors[0].copy(alpha = 0.6f),
                            pack.gradientColors[1].copy(alpha = 0.6f)
                        )
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon & Package details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circle Emoji Container
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    pack.gradientColors[0].copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                        .border(BorderStroke(1.dp, pack.gradientColors[0].copy(alpha = 0.4f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = pack.emoji, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pack.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif
                        )
                        if (pack.highlight != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(pack.gradientColors[1], shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = pack.highlight,
                                    fontSize = 8.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = pack.description,
                        fontSize = 10.sp,
                        color = Color(0x99FFFFFF),
                        fontFamily = FontFamily.SansSerif
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🪙 ${String.format("%,d", pack.coinAmount)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD700),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = " COINS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFD700),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Price Button
            Button(
                onClick = onPurchaseClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .width(90.dp)
                    .height(38.dp)
                    .border(
                        BorderStroke(1.5.dp, Brush.horizontalGradient(pack.gradientColors)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                pack.gradientColors[0].copy(alpha = 0.2f),
                                pack.gradientColors[1].copy(alpha = 0.2f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Text(
                    text = displayPrice,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// Particle class for success animation
class ConfettiParticle(
    val x: Float,
    val y: Float,
    val speedX: Float,
    val speedY: Float,
    val color: Color,
    val size: Float,
    var currentX: Float = x,
    var currentY: Float = y
)

@Composable
fun PurchaseSuccessOverlay(
    grantedAmount: Int,
    onDismiss: () -> Unit
) {
    val particles = remember {
        List(100) {
            val angle = Random.nextDouble(0.0, 2.0 * Math.PI)
            val speed = Random.nextDouble(2.0, 15.0).toFloat()
            ConfettiParticle(
                x = 0.5f,
                y = 0.4f,
                speedX = (Math.cos(angle) * speed).toFloat(),
                speedY = (Math.sin(angle) * speed).toFloat(),
                color = when (Random.nextInt(4)) {
                    0 -> Color(0xFFFFD700) // Gold
                    1 -> Color(0xFF00FFFF) // Cyan
                    2 -> Color(0xFFFF007F) // Pink
                    else -> Color(0xFFFFFFFF) // White
                },
                size = Random.nextDouble(4.0, 14.0).toFloat()
            )
        }
    }

    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val anim = TargetAnimation(0f, 1f, durationMillis = 1800) {
            progress = it
        }
        delay(3000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE002020A))
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        // Draw Confetti Particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height * 0.4f

            for (p in particles) {
                // Apply drag & gravity
                val px = centerX + (p.speedX * progress * 80)
                // Parabolic fall
                val py = centerY + (p.speedY * progress * 80) + (progress * progress * 250)
                
                drawCircle(
                    color = p.color.copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                    radius = p.size,
                    center = Offset(px, py)
                )
            }
        }

        // Animated Success Content Card
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color(0xFF0C1033), shape = RoundedCornerShape(20.dp))
                .border(
                    BorderStroke(2.dp, Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500)))),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0x22FFD700), CircleShape)
                    .border(BorderStroke(2.dp, Color(0xFFFFD700)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🪙", fontSize = 42.sp)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "PURCHASE SUCCESSFUL!",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFD700),
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Secure payment was authorized successfully. Your galaxy account has been credited with:",
                fontSize = 11.sp,
                color = Color.LightGray,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "+${String.format("%,d", grantedAmount)} Coins",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(44.dp)
            ) {
                Text(
                    text = "AWESOME!",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

private suspend fun TargetAnimation(
    from: Float,
    to: Float,
    durationMillis: Int,
    onFrame: (Float) -> Unit
) {
    val startTime = System.currentTimeMillis()
    while (true) {
        val elapsed = System.currentTimeMillis() - startTime
        val rawFraction = elapsed.toFloat() / durationMillis
        val fraction = rawFraction.coerceIn(0f, 1f)
        onFrame(from + (to - from) * fraction)
        if (elapsed >= durationMillis) {
            break
        }
        delay(16) // ~60fps
    }
}
