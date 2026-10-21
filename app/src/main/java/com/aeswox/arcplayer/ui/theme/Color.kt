package com.aeswox.arcplayer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Frosted Jelly neutral foundation — the AMOLED dark roles (Tokens/TOKENS.md).
 *
 * The app is dark only, so these are the values the whole UI reads through
 * [ArcPlayerJellyTheme]; the light set the system also defines is not carried here. Values match
 * Arc Music's `ui/theme/Color.kt` dark palette, which is the reference implementation: a black
 * page, containers lifted one step at a time, and soft white text. Nothing in the UI should
 * hardcode one of these — ask the scheme for the role.
 */
val Background = Color(0xFF000000)
val OnBackground = Color(0xFFEEEEEE)
val Surface = Color(0xFF0D0D0D)
val SurfaceDim = Color(0xFF080808)
val SurfaceBright = Color(0xFF1A1A1A)
val SurfaceContainerLowest = Color(0xFF000000)
val SurfaceContainerLow = Color(0xFF0D0D0D)
val SurfaceContainer = Color(0xFF141414)
val SurfaceContainerHigh = Color(0xFF1E1E1E)
val SurfaceContainerHighest = Color(0xFF282828)
val SurfaceVariant = Color(0xFF1A1A1A)
val OnSurface = Color(0xFFE8E8E8)
val OnSurfaceVariant = Color(0xFFAAAAAA)
val SurfaceTint = Color(0xFFCCCCCC)
val InverseSurface = Color(0xFFE8E8E8)
val InverseOnSurface = Color(0xFF1A1A1A)
val Outline = Color(0xFF444444)
val OutlineVariant = Color(0xFF2A2A2A)
val Primary = Color(0xFFFFFFFF)
val OnPrimary = Color(0xFF000000)
val PrimaryContainer = Color(0xFF1E1E1E)
val OnPrimaryContainer = Color(0xFFCCCCCC)
val InversePrimary = Color(0xFF333333)
val Secondary = Color(0xFFAAAAAA)
val OnSecondary = Color(0xFF1A1A1A)
val SecondaryContainer = Color(0xFF1E1E1E)
val OnSecondaryContainer = Color(0xFFCCCCCC)
val Tertiary = Color(0xFFCCCCCC)
val OnTertiary = Color(0xFF0D0D0D)
val TertiaryContainer = Color(0xFF1A1F28)
val OnTertiaryContainer = Color(0xFFB0B8C8)
val Error = Color(0xFFFF6B6B)
val OnError = Color(0xFF000000)
val ErrorContainer = Color(0xFF3A0000)
val OnErrorContainer = Color(0xFFFF8A80)

/**
 * The one non-neutral colour in the app, for marks that mean "this is the active one" — a
 * favourited title, a watch-progress fill, a recent dot. Frosted Jelly keeps the foundation
 * monochrome and lets artwork supply colour, so the system has no brand accent to take; this is
 * Arc Music's documented accent fallback (`ArcNowPlayingScreen.kt`), used there until artwork
 * supplies a dominant colour.
 */
val Accent = Color(0xFFB28D84)
