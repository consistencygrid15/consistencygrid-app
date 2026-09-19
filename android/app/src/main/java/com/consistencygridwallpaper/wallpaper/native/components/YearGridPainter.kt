package com.consistencygridwallpaper.wallpaper.native.components

// YearGridPainter is superseded by WeeksGridPainter + DaysGridPainter + MonthGridPainter.
// All grid modes are now handled via GridSectionPainter which delegates to the
// respective mode-specific painter.
// This file is kept to avoid breaking any stale references during compilation.
@Deprecated("Use GridSectionPainter which delegates to WeeksGridPainter, DaysGridPainter, etc.")
object YearGridPainter
