package com.halovoid.bunori.ui.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class ThemePreviewColors(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val text: Color
)

data class AppTheme(
    val id: String,
    val name: String,
    val description: String = "",
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
    val previewLight: ThemePreviewColors,
    val previewDark: ThemePreviewColors,
    val isDynamic: Boolean = false
)

object ThemeRegistry {
    // 1. Default (Velvet Dusk / Aether Violet) - Premium Amethyst Violet & Deep Midnight
    val DefaultTheme = AppTheme(
        id = "DEFAULT",
        name = "Default",
        description = "Signature Velvet Amethyst & deep midnight violet",
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF7C3AED), // Rich Royal Violet
            secondary = Color(0xFF6D28D9), // Deep Violet Accent
            tertiary = ErrorRed,
            background = Color(0xFFF8F7FF), // Soft Lilac Tinted White
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEDE9FE), // Light Periwinkle Container
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF1E1B2E), // Deep Plum Text
            onSurface = Color(0xFF1E1B2E),
            onSurfaceVariant = Color(0xFF6C6382), // Muted Purple-Gray Text
            outline = Color(0xFFDDD6FE),
            outlineVariant = Color(0xFFEDE9FE)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFA78BFA), // Luminous Amethyst Violet
            secondary = Color(0xFFC4B5FD), // Soft Lavender Accent
            tertiary = ErrorRed,
            background = Color(0xFF12111A), // Deep Midnight Velvet
            surface = Color(0xFF1B1926), // Velvet Card Surface
            surfaceVariant = Color(0xFF262336), // Velvet Surface Variant
            onPrimary = Color(0xFF12111A),
            onSecondary = Color(0xFF12111A),
            onTertiary = Color.White,
            onBackground = Color(0xFFF5F3FF), // Crisp White with Warm Lilac Tint
            onSurface = Color(0xFFF5F3FF),
            onSurfaceVariant = Color(0xFFA7ACD9), // Secondary Text with Periwinkle Tint
            outline = Color(0xFF332F47), // Subtle Violet-Tinted Border
            outlineVariant = Color(0xFF262336)
        ),
        previewLight = ThemePreviewColors(
            primary = Color(0xFF7C3AED),
            secondary = Color(0xFF6D28D9),
            background = Color(0xFFF8F7FF),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF1E1B2E)
        ),
        previewDark = ThemePreviewColors(
            primary = Color(0xFFA78BFA),
            secondary = Color(0xFFC4B5FD),
            background = Color(0xFF12111A),
            surface = Color(0xFF1B1926),
            text = Color(0xFFF5F3FF)
        )
    )

    // 2. Dynamic (Material You)
    val DynamicTheme = AppTheme(
        id = "DYNAMIC",
        name = "Dynamic",
        description = "Adaptive system wallpaper colors (Android 12+)",
        lightColorScheme = DefaultTheme.lightColorScheme,
        darkColorScheme = DefaultTheme.darkColorScheme,
        previewLight = ThemePreviewColors(
            primary = Color(0xFF0284C7),
            secondary = Color(0xFF0EA5E9),
            background = Color(0xFFF0F9FF),
            surface = Color(0xFFE0F2FE),
            text = Color(0xFF0C4A6E)
        ),
        previewDark = ThemePreviewColors(
            primary = Color(0xFF38BDF8),
            secondary = Color(0xFF7DD3FC),
            background = Color(0xFF0C2A4A),
            surface = Color(0xFF163B5F),
            text = Color(0xFFF0F9FF)
        ),
        isDynamic = true
    )

    // 3. Catppuccin (Mocha & Latte)
    val CatppuccinTheme = AppTheme(
        id = "CATPPUCCIN",
        name = "Catppuccin",
        description = "Soothing pastel palette with soft mauve & sapphire",
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF8839EF), // Mauve
            secondary = Color(0xFF1E66F5), // Blue
            tertiary = Color(0xFFD20F39), // Red
            background = Color(0xFFEFF1F5), // Base
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE6E9EF), // Mantle
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF4C4F69), // Text
            onSurface = Color(0xFF4C4F69),
            onSurfaceVariant = Color(0xFF6C6F85),
            outline = Color(0xFFCCD0DA),
            outlineVariant = Color(0xFFE6E9EF)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFCBA6F7), // Mauve
            secondary = Color(0xFF89B4FA), // Blue
            tertiary = Color(0xFFF38BA8), // Red
            background = Color(0xFF181825), // Mantle
            surface = Color(0xFF1E1E2E), // Base
            surfaceVariant = Color(0xFF313244), // Surface0
            onPrimary = Color(0xFF11111B), // Crust
            onSecondary = Color(0xFF11111B),
            onTertiary = Color(0xFF11111B),
            onBackground = Color(0xFFCDD6F4), // Text
            onSurface = Color(0xFFCDD6F4),
            onSurfaceVariant = Color(0xFFA6ADC8), // Subtext0
            outline = Color(0xFF45475A),
            outlineVariant = Color(0xFF313244)
        ),
        previewLight = ThemePreviewColors(
            primary = Color(0xFF8839EF),
            secondary = Color(0xFF1E66F5),
            background = Color(0xFFEFF1F5),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF4C4F69)
        ),
        previewDark = ThemePreviewColors(
            primary = Color(0xFFCBA6F7),
            secondary = Color(0xFF89B4FA),
            background = Color(0xFF181825),
            surface = Color(0xFF1E1E2E),
            text = Color(0xFFCDD6F4)
        )
    )

    // 4. Dracula
    val DraculaTheme = AppTheme(
        id = "DRACULA",
        name = "Dracula",
        description = "Gothic dark aesthetic with vivid purple & neon pink",
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF7B1FA2),
            secondary = Color(0xFFD81B60),
            tertiary = Color(0xFFE53935),
            background = Color(0xFFF7F7FA),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFECEEF4),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF282A36),
            onSurface = Color(0xFF282A36),
            onSurfaceVariant = Color(0xFF6272A4),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFBD93F9), // Purple
            secondary = Color(0xFFFF79C6), // Pink
            tertiary = Color(0xFFFF5555), // Red
            background = Color(0xFF1E1F29),
            surface = Color(0xFF282A36), // Current line
            surfaceVariant = Color(0xFF383A59),
            onPrimary = Color(0xFF282A36),
            onSecondary = Color(0xFF282A36),
            onTertiary = Color(0xFF282A36),
            onBackground = Color(0xFFF8F8F2),
            onSurface = Color(0xFFF8F8F2),
            onSurfaceVariant = Color(0xFF6272A4),
            outline = Color(0xFF44475A),
            outlineVariant = Color(0xFF343746)
        ),
        previewLight = ThemePreviewColors(
            primary = Color(0xFF7B1FA2),
            secondary = Color(0xFFD81B60),
            background = Color(0xFFF7F7FA),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF282A36)
        ),
        previewDark = ThemePreviewColors(
            primary = Color(0xFFBD93F9),
            secondary = Color(0xFFFF79C6),
            background = Color(0xFF1E1F29),
            surface = Color(0xFF282A36),
            text = Color(0xFFF8F8F2)
        )
    )

    // 5. Tokyo Night
    val TokyoNightTheme = AppTheme(
        id = "TOKYO_NIGHT",
        name = "Tokyo Night",
        description = "Cyberpunk deep indigo night with electric cyan",
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF2E5CB8),
            secondary = Color(0xFF7052B4),
            tertiary = Color(0xFF8C4351),
            background = Color(0xFFF2F3F7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE2E4EC),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF343B58),
            onSurface = Color(0xFF343B58),
            onSurfaceVariant = Color(0xFF68708E),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFDFE2EA)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFF7AA2F7), // Tokyo Blue
            secondary = Color(0xFFBB9AF7), // Purple
            tertiary = Color(0xFFF7768E), // Red
            background = Color(0xFF16161E),
            surface = Color(0xFF1F2335),
            surfaceVariant = Color(0xFF292E42),
            onPrimary = Color(0xFF16161E),
            onSecondary = Color(0xFF16161E),
            onTertiary = Color(0xFF16161E),
            onBackground = Color(0xFFC0CAF5),
            onSurface = Color(0xFFC0CAF5),
            onSurfaceVariant = Color(0xFF7982A9),
            outline = Color(0xFF3B4261),
            outlineVariant = Color(0xFF24283B)
        ),
        previewLight = ThemePreviewColors(
            primary = Color(0xFF2E5CB8),
            secondary = Color(0xFF7052B4),
            background = Color(0xFFF2F3F7),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF343B58)
        ),
        previewDark = ThemePreviewColors(
            primary = Color(0xFF7AA2F7),
            secondary = Color(0xFFBB9AF7),
            background = Color(0xFF16161E),
            surface = Color(0xFF1F2335),
            text = Color(0xFFC0CAF5)
        )
    )

    // 6. Nord
    val NordTheme = AppTheme(
        id = "NORD",
        name = "Nord",
        description = "Arctic frost serenity with polar dark & ice blue",
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF4C7A9E),
            secondary = Color(0xFF5E81AC),
            tertiary = Color(0xFFBF616A),
            background = Color(0xFFECEFF4),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE5E9F0),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF2E3440),
            onSurface = Color(0xFF2E3440),
            onSurfaceVariant = Color(0xFF4C566A),
            outline = Color(0xFFD8DEE9),
            outlineVariant = Color(0xFFE5E9F0)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFF88C0D0), // Frost Blue
            secondary = Color(0xFF81A1C1),
            tertiary = Color(0xFFBF616A),
            background = Color(0xFF242933),
            surface = Color(0xFF2E3440),
            surfaceVariant = Color(0xFF3B4252),
            onPrimary = Color(0xFF2E3440),
            onSecondary = Color(0xFF2E3440),
            onTertiary = Color(0xFF2E3440),
            onBackground = Color(0xFFECEFF4),
            onSurface = Color(0xFFECEFF4),
            onSurfaceVariant = Color(0xFFD8DEE9),
            outline = Color(0xFF4C566A),
            outlineVariant = Color(0xFF353D4C)
        ),
        previewLight = ThemePreviewColors(
            primary = Color(0xFF4C7A9E),
            secondary = Color(0xFF5E81AC),
            background = Color(0xFFECEFF4),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF2E3440)
        ),
        previewDark = ThemePreviewColors(
            primary = Color(0xFF88C0D0),
            secondary = Color(0xFF81A1C1),
            background = Color(0xFF242933),
            surface = Color(0xFF2E3440),
            text = Color(0xFFECEFF4)
        )
    )

    // 7. Sakura (Rosé Pine)
    val SakuraTheme = AppTheme(
        id = "SAKURA",
        name = "Sakura",
        description = "Warm dusk & Japanese cherry blossom rose tones",
        lightColorScheme = lightColorScheme(
            primary = Color(0xFFB4637A),
            secondary = Color(0xFFD7827E),
            tertiary = Color(0xFFB4637A),
            background = Color(0xFFFAF4ED),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF2E9DE),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF575279),
            onSurface = Color(0xFF575279),
            onSurfaceVariant = Color(0xFF797593),
            outline = Color(0xFFCECAC3),
            outlineVariant = Color(0xFFDFDAD2)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFEB6F92), // Love / Rose
            secondary = Color(0xFFEBBCBA), // Rose Gold
            tertiary = Color(0xFFEB6F92),
            background = Color(0xFF14121E),
            surface = Color(0xFF1F1D2E),
            surfaceVariant = Color(0xFF26233A),
            onPrimary = Color(0xFF191724),
            onSecondary = Color(0xFF191724),
            onTertiary = Color(0xFF191724),
            onBackground = Color(0xFFE0DEF4),
            onSurface = Color(0xFFE0DEF4),
            onSurfaceVariant = Color(0xFF908CAA),
            outline = Color(0xFF403D52),
            outlineVariant = Color(0xFF26233A)
        ),
        previewLight = ThemePreviewColors(
            primary = Color(0xFFB4637A),
            secondary = Color(0xFFD7827E),
            background = Color(0xFFFAF4ED),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF575279)
        ),
        previewDark = ThemePreviewColors(
            primary = Color(0xFFEB6F92),
            secondary = Color(0xFFEBBCBA),
            background = Color(0xFF14121E),
            surface = Color(0xFF1F1D2E),
            text = Color(0xFFE0DEF4)
        )
    )

    // 8. Emerald Forest
    val EmeraldTheme = AppTheme(
        id = "EMERALD",
        name = "Emerald",
        description = "Botanical forest calm with vibrant mint & emerald",
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF059669),
            secondary = Color(0xFF10B981),
            tertiary = ErrorRed,
            background = Color(0xFFF0FDF4),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFDCFCE7),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF064E3B),
            onSurface = Color(0xFF064E3B),
            onSurfaceVariant = Color(0xFF047857),
            outline = Color(0xFFA7F3D0),
            outlineVariant = Color(0xFFDCFCE7)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFF10B981), // Emerald
            secondary = Color(0xFF34D399), // Mint
            tertiary = ErrorRed,
            background = Color(0xFF0B1411),
            surface = Color(0xFF13221C),
            surfaceVariant = Color(0xFF1B3128),
            onPrimary = Color(0xFF062016),
            onSecondary = Color(0xFF062016),
            onTertiary = Color(0xFF062016),
            onBackground = Color(0xFFECFDF5),
            onSurface = Color(0xFFECFDF5),
            onSurfaceVariant = Color(0xFFA7F3D0),
            outline = Color(0xFF27473A),
            outlineVariant = Color(0xFF182C24)
        ),
        previewLight = ThemePreviewColors(
            primary = Color(0xFF059669),
            secondary = Color(0xFF10B981),
            background = Color(0xFFF0FDF4),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF064E3B)
        ),
        previewDark = ThemePreviewColors(
            primary = Color(0xFF10B981),
            secondary = Color(0xFF34D399),
            background = Color(0xFF0B1411),
            surface = Color(0xFF13221C),
            text = Color(0xFFECFDF5)
        )
    )

    // 9. Crimson Dusk
    val CrimsonTheme = AppTheme(
        id = "CRIMSON",
        name = "Crimson Dusk",
        description = "High-contrast dramatic ruby tones & dark obsidian",
        lightColorScheme = lightColorScheme(
            primary = Color(0xFFE11D48),
            secondary = Color(0xFFBE123C),
            tertiary = Color(0xFF881337),
            background = Color(0xFFFFF1F2),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFFFE4E6),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF4C0519),
            onSurface = Color(0xFF4C0519),
            onSurfaceVariant = Color(0xFF9F1239),
            outline = Color(0xFFFECDD3),
            outlineVariant = Color(0xFFFFE4E6)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFF43F5E), // Rose Crimson
            secondary = Color(0xFFFB7185), // Coral
            tertiary = Color(0xFFE11D48),
            background = Color(0xFF120B0E),
            surface = Color(0xFF1C1217),
            surfaceVariant = Color(0xFF2A1B22),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color(0xFFFFF1F2),
            onSurface = Color(0xFFFFF1F2),
            onSurfaceVariant = Color(0xFFFDA4AF),
            outline = Color(0xFF3D2732),
            outlineVariant = Color(0xFF23161C)
        ),
        previewLight = ThemePreviewColors(
            primary = Color(0xFFE11D48),
            secondary = Color(0xFFBE123C),
            background = Color(0xFFFFF1F2),
            surface = Color(0xFFFFFFFF),
            text = Color(0xFF4C0519)
        ),
        previewDark = ThemePreviewColors(
            primary = Color(0xFFF43F5E),
            secondary = Color(0xFFFB7185),
            background = Color(0xFF120B0E),
            surface = Color(0xFF1C1217),
            text = Color(0xFFFFF1F2)
        )
    )

    // 10. Midnight OLED
    val MidnightOledTheme = AppTheme(
        id = "MIDNIGHT_OLED",
        name = "Midnight OLED",
        description = "Pure pitch black with vivid violet for maximum contrast",
        lightColorScheme = DefaultTheme.lightColorScheme,
        darkColorScheme = darkColorScheme(
            primary = Color(0xFF8B5CF6), // Vivid Violet
            secondary = Color(0xFFA78BFA),
            tertiary = ErrorRed,
            background = Color.Black,
            surface = Color(0xFF0E0E0E),
            surfaceVariant = Color(0xFF181818),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color(0xFFF9FAFB),
            onSurface = Color(0xFFF9FAFB),
            onSurfaceVariant = Color(0xFF9CA3AF),
            outline = Color(0xFF222222),
            outlineVariant = Color(0xFF161616)
        ),
        previewLight = DefaultTheme.previewLight,
        previewDark = ThemePreviewColors(
            primary = Color(0xFF8B5CF6),
            secondary = Color(0xFFA78BFA),
            background = Color.Black,
            surface = Color(0xFF0E0E0E),
            text = Color(0xFFF9FAFB)
        )
    )

    val allThemes: List<AppTheme> = listOf(
        DefaultTheme,
        DynamicTheme,
        CatppuccinTheme,
        DraculaTheme,
        TokyoNightTheme,
        NordTheme,
        SakuraTheme,
        EmeraldTheme,
        CrimsonTheme,
        MidnightOledTheme
    )

    fun getThemeById(id: String): AppTheme {
        return allThemes.find { it.id.equals(id, ignoreCase = true) } ?: DefaultTheme
    }
}
