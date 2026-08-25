package com.earthvideo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette (fixed)
val Primary = Color(0xFF3B6EE5)
val PrimaryDark = Color(0xFF2A5ACF)
val PrimaryLight = Color(0xFF5A8CF7)
val PrimarySoft = Color(0xFFE8EFFE)
val PrimaryDeep = Color(0xFF1E4CC4)

// Functional palette (fixed)
val HotRed = Color(0xFFF44336)
val HotRedDark = Color(0xFFD32F2F)
val Gold = Color(0xFFFFC107)
val Orange = Color(0xFFFF9800)
val Green = Color(0xFF4CAF50)
val Purple = Color(0xFF7C4DFF)

// Neutral surface palette (light mode defaults)
val PageBg = Color(0xFFF7F8FA)
val CardBg = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFF1F3F6)

// Text palette (light mode defaults)
val TextPrimary = Color(0xFF212121)
val TextSecondary = Color(0xFF666666)
val TextHint = Color(0xFF999999)
val TextDisabled = Color(0xFFBDBDBD)

// Border / divider palette (light mode defaults)
val Divider = Color(0xFFEEEEEE)
val DividerStrong = Color(0xFFE0E0E0)
val TabBg = Color(0xFFF5F5F5)
val ChipBg = Color(0xFFEEF1F6)

// Player palette (always dark)
val PlayerBg = Color(0xFF000000)
val PlayerOverlay = Color(0xCC000000)
val PlayerOverlayLight = Color(0x80000000)
val PlayerHint = Color(0x66FFFFFF)
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val SemiBlack = Color(0x80000000)
val LabelBg = Color(0xFFF5F5F5)
val SourceBadgeBg = Color(0xFF37474F)

// Shadow tint
val ShadowTint = Color(0x14000000)
val ShadowTintSoft = Color(0x08000000)

// Rank badges
val RankSilver = Color(0xFFB0BEC5)
val RankBronze = Color(0xFFCD7F32)

// ── Theme-aware semantic colors (for use inside @Composable functions) ──
@Composable fun themePageBg(): Color = MaterialTheme.colorScheme.background
@Composable fun themeCardBg(): Color = MaterialTheme.colorScheme.surface
@Composable fun themeTextPrimary(): Color = MaterialTheme.colorScheme.onSurface
@Composable fun themeTextSecondary(): Color = MaterialTheme.colorScheme.onSurfaceVariant
@Composable fun themeTextHint(): Color = MaterialTheme.colorScheme.outline