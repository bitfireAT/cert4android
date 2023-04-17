package at.bitfire.cert4android

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class ParcelableColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceTint: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color
) : Parcelable {
    constructor(parcel: Parcel) : this(
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt()),
        Color(parcel.readInt())
    )

    constructor(colorScheme: ColorScheme) : this(
        colorScheme.primary,
        colorScheme.onPrimary,
        colorScheme.primaryContainer,
        colorScheme.onPrimaryContainer,
        colorScheme.inversePrimary,
        colorScheme.secondary,
        colorScheme.onSecondary,
        colorScheme.secondaryContainer,
        colorScheme.onSecondaryContainer,
        colorScheme.tertiary,
        colorScheme.onTertiary,
        colorScheme.tertiaryContainer,
        colorScheme.onTertiaryContainer,
        colorScheme.background,
        colorScheme.onBackground,
        colorScheme.surface,
        colorScheme.onSurface,
        colorScheme.surfaceVariant,
        colorScheme.onSurfaceVariant,
        colorScheme.surfaceTint,
        colorScheme.inverseSurface,
        colorScheme.inverseOnSurface,
        colorScheme.error,
        colorScheme.onError,
        colorScheme.errorContainer,
        colorScheme.onErrorContainer,
        colorScheme.outline,
        colorScheme.outlineVariant,
        colorScheme.scrim
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(primary.toArgb())
        parcel.writeInt(onPrimary.toArgb())
        parcel.writeInt(primaryContainer.toArgb())
        parcel.writeInt(onPrimaryContainer.toArgb())
        parcel.writeInt(inversePrimary.toArgb())
        parcel.writeInt(secondary.toArgb())
        parcel.writeInt(onSecondary.toArgb())
        parcel.writeInt(secondaryContainer.toArgb())
        parcel.writeInt(onSecondaryContainer.toArgb())
        parcel.writeInt(tertiary.toArgb())
        parcel.writeInt(onTertiary.toArgb())
        parcel.writeInt(tertiaryContainer.toArgb())
        parcel.writeInt(onTertiaryContainer.toArgb())
        parcel.writeInt(background.toArgb())
        parcel.writeInt(onBackground.toArgb())
        parcel.writeInt(surface.toArgb())
        parcel.writeInt(onSurface.toArgb())
        parcel.writeInt(surfaceVariant.toArgb())
        parcel.writeInt(onSurfaceVariant.toArgb())
        parcel.writeInt(surfaceTint.toArgb())
        parcel.writeInt(inverseSurface.toArgb())
        parcel.writeInt(inverseOnSurface.toArgb())
        parcel.writeInt(error.toArgb())
        parcel.writeInt(onError.toArgb())
        parcel.writeInt(errorContainer.toArgb())
        parcel.writeInt(onErrorContainer.toArgb())
        parcel.writeInt(outline.toArgb())
        parcel.writeInt(outlineVariant.toArgb())
        parcel.writeInt(scrim.toArgb())
    }

    fun toColorScheme(): ColorScheme = ColorScheme(
        primary,
        onPrimary,
        primaryContainer,
        onPrimaryContainer,
        inversePrimary,
        secondary,
        onSecondary,
        secondaryContainer,
        onSecondaryContainer,
        tertiary,
        onTertiary,
        tertiaryContainer,
        onTertiaryContainer,
        background,
        onBackground,
        surface,
        onSurface,
        surfaceVariant,
        onSurfaceVariant,
        surfaceTint,
        inverseSurface,
        inverseOnSurface,
        error,
        onError,
        errorContainer,
        onErrorContainer,
        outline,
        outlineVariant,
        scrim
    )

    companion object CREATOR : Parcelable.Creator<ParcelableColorScheme> {
        override fun createFromParcel(parcel: Parcel): ParcelableColorScheme =
            ParcelableColorScheme(parcel)

        override fun newArray(size: Int): Array<ParcelableColorScheme?> = arrayOfNulls(size)
    }
}

/** Converts the color scheme into [ParcelableColorScheme] */
fun ColorScheme.parcelize(): ParcelableColorScheme = ParcelableColorScheme(this)
