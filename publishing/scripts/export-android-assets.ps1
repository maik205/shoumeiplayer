param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$sourceRoot = Join-Path $RepositoryRoot "publishing/brand/source"
$resRoot = Join-Path $RepositoryRoot "app/src/main/res"
$appIconSource = Join-Path $sourceRoot "app_icon.png"
$bannerSource = Join-Path $sourceRoot "banner.png"

foreach ($source in @($appIconSource, $bannerSource)) {
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Missing required brand source: $source"
    }
}

function New-Directory([string]$Path) {
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
}

function Save-ResizedImage(
    [System.Drawing.Image]$Source,
    [string]$Destination,
    [int]$Width,
    [int]$Height,
    [System.Drawing.Imaging.PixelFormat]$PixelFormat
) {
    New-Directory (Split-Path -Parent $Destination)
    $bitmap = [System.Drawing.Bitmap]::new($Width, $Height, $PixelFormat)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.DrawImage($Source, 0, 0, $Width, $Height)
        $bitmap.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$appIcon = [System.Drawing.Image]::FromFile($appIconSource)
$banner = [System.Drawing.Image]::FromFile($bannerSource)

try {
    # Google TV must resolve the density-specific app_icon.png exports directly.
    # Remove the old adaptive override if this script is run over an older checkout.
    foreach ($staleOverride in @(
        (Join-Path $resRoot "drawable-nodpi/ic_launcher_foreground.png"),
        (Join-Path $resRoot "mipmap-anydpi-v26/ic_launcher.xml")
    )) {
        if (Test-Path -LiteralPath $staleOverride) {
            Remove-Item -LiteralPath $staleOverride
        }
    }

    $densityExports = @(
        @{ Qualifier = "mdpi"; Icon = 80; BannerWidth = 160; BannerHeight = 90 },
        @{ Qualifier = "hdpi"; Icon = 120; BannerWidth = 240; BannerHeight = 135 },
        @{ Qualifier = "xhdpi"; Icon = 160; BannerWidth = 320; BannerHeight = 180 },
        @{ Qualifier = "xxhdpi"; Icon = 240; BannerWidth = 480; BannerHeight = 270 },
        @{ Qualifier = "xxxhdpi"; Icon = 320; BannerWidth = 640; BannerHeight = 360 }
    )

    foreach ($export in $densityExports) {
        $mipmap = Join-Path $resRoot "mipmap-$($export.Qualifier)"
        New-Directory $mipmap
        $legacyWebp = Join-Path $mipmap "ic_launcher.webp"
        if (Test-Path -LiteralPath $legacyWebp) {
            Remove-Item -LiteralPath $legacyWebp
        }
        Save-ResizedImage `
            -Source $appIcon `
            -Destination (Join-Path $mipmap "ic_launcher.png") `
            -Width $export.Icon `
            -Height $export.Icon `
            -PixelFormat ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
        Save-ResizedImage `
            -Source $banner `
            -Destination (Join-Path $mipmap "ic_launcher_banner.png") `
            -Width $export.BannerWidth `
            -Height $export.BannerHeight `
            -PixelFormat ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    }

    Save-ResizedImage `
        -Source $appIcon `
        -Destination (Join-Path $RepositoryRoot "publishing/google-play/app-icon/app-icon-512.png") `
        -Width 512 `
        -Height 512 `
        -PixelFormat ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

    Save-ResizedImage `
        -Source $banner `
        -Destination (Join-Path $RepositoryRoot "publishing/google-play/tv-banner/tv-banner-1280x720.png") `
        -Width 1280 `
        -Height 720 `
        -PixelFormat ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
} finally {
    $appIcon.Dispose()
    $banner.Dispose()
}

Write-Host "Exported Shoumei Player Android and Google Play icon assets."
