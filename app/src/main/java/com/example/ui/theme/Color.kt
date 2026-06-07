package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Sleek Interface Theme Colors
// ==========================================

// Canvas and Backgrounds
val SleekBg = Color(0xFFF7F9FC)
val SleekWhite = Color(0xFFFFFFFF)
val SleekTextPrimary = Color(0xFF1B1B1F)
val SleekTextSecondary = Color(0xFF44474E)
val SleekTextMuted = Color(0xFF74777F)

// Blue Brand & Accent Palette
val SleekPrimary = Color(0xFF0061A4)
val SleekPrimaryContainer = Color(0xFFD1E4FF)
val SleekPrimaryDark = Color(0xFF004A77)
val SleekOnPrimaryContainer = Color(0xFF001D31)

// Borders & Outlines
val SleekBorderLight = Color(0xFFE1E2EC)
val SleekBorderMedium = Color(0xFFC4C7CF)

// Dark Contrast Canvas (Used for Defensive maps & high-contrast rotation sections)
val SleekDarkBg = Color(0xFF1B1B1F)
val SleekDarkCardBg = Color(0xFF2E3036)
val SleekDarkBorder = Color(0x1AFFFFFF) // 10% white

// Map Existing Theme Aliases for Safe Compilation & Clean Transition
val StadiumDarkBg = SleekBg                    // High-contrast clean canvas
val StadiumSlateSurface = SleekWhite           // Card surface white
val OutfieldGreen = SleekPrimary               // Standard brand accent
val ClayAmber = Color(0xFFF97316)              // Clay dirt orange/amber
val BaseballWhite = SleekTextPrimary           // High-contrast primary text
val TurfLime = Color(0xFF84CC16)               // Accent highlight lime
val SkyOutfield = Color(0xFF0EA5E9)            // Cool accent blue
val StadiumGrayBorder = SleekBorderLight       // Cool grey outline border
val TextSecondary = SleekTextSecondary         // Muted secondary text

