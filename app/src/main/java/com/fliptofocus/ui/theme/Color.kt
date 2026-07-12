package com.fliptofocus.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// iOS-style dark system palette (the app is dark-only for a clean, pro look).
// ---------------------------------------------------------------------------
val IosBackground = Color(0xFF000000) // screen background
val IosGroup = Color(0xFF1C1C1E)      // grouped content / cards
val IosNested = Color(0xFF2C2C2E)     // nested elements / pressed
val IosSeparator = Color(0xFF38383A)  // hairline separators / outlines
val IosLabel = Color(0xFFFFFFFF)      // primary text
val IosSecondaryLabel = Color(0xFF98989F) // secondary text
val IosBlue = Color(0xFF0A84FF)       // accent
val IosGreen = Color(0xFF30D158)      // success / completed
val IosOrange = Color(0xFFFF9F0A)     // warning / abandoned
val IosRed = Color(0xFFFF453A)        // destructive

// Material color-scheme values (dark). The Light* set is retained but unused.
val PrimaryLight = IosBlue
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFF143A5A)
val OnPrimaryContainerLight = Color(0xFFD6E4FF)
val SecondaryLight = IosGreen
val OnSecondaryLight = Color(0xFF00210F)
val SecondaryContainerLight = Color(0xFF0B3D24)
val OnSecondaryContainerLight = Color(0xFFB8F5C9)
val BackgroundLight = IosBackground
val OnBackgroundLight = IosLabel
val SurfaceLight = IosGroup
val OnSurfaceLight = IosLabel
val SurfaceVariantLight = IosNested
val OnSurfaceVariantLight = IosSecondaryLabel
val ErrorLight = IosRed
val OnErrorLight = Color(0xFFFFFFFF)
val OutlineLight = IosSeparator

val PrimaryDark = IosBlue
val OnPrimaryDark = Color(0xFFFFFFFF)
val PrimaryContainerDark = Color(0xFF143A5A)
val OnPrimaryContainerDark = Color(0xFFD6E4FF)
val SecondaryDark = IosGreen
val OnSecondaryDark = Color(0xFF00210F)
val SecondaryContainerDark = Color(0xFF0B3D24)
val OnSecondaryContainerDark = Color(0xFFB8F5C9)
val BackgroundDark = IosBackground
val OnBackgroundDark = IosLabel
val SurfaceDark = IosGroup
val OnSurfaceDark = IosLabel
val SurfaceVariantDark = IosNested
val OnSurfaceVariantDark = IosSecondaryLabel
val ErrorDark = IosRed
val OnErrorDark = Color(0xFFFFFFFF)
val OutlineDark = IosSeparator

// Legacy accents.
val PositionValidGreen = IosGreen
val PositionInvalidAmber = IosOrange
