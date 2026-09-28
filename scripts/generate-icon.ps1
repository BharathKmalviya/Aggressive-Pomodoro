$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$resources = Join-Path $PSScriptRoot '..\desktopApp\resources'
$bitmap = [System.Drawing.Bitmap]::new(256, 256)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::FromArgb(217, 72, 53))
$white = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
$dark = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(23, 23, 23))
$ring = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 17)
try {
    $graphics.DrawEllipse($ring, 44, 44, 168, 168)
    $graphics.FillEllipse($dark, 75, 75, 106, 106)
    $points = [System.Drawing.Point[]]@(
        [System.Drawing.Point]::new(137, 78),
        [System.Drawing.Point]::new(94, 139),
        [System.Drawing.Point]::new(125, 139),
        [System.Drawing.Point]::new(113, 184),
        [System.Drawing.Point]::new(166, 117),
        [System.Drawing.Point]::new(135, 117)
    )
    $graphics.FillPolygon($white, $points)
    $pngPath = Join-Path $resources 'app.png'
    $icoPath = Join-Path $resources 'app.ico'
    $bitmap.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $png = [System.IO.File]::ReadAllBytes($pngPath)
    $stream = [System.IO.File]::Create($icoPath)
    $writer = [System.IO.BinaryWriter]::new($stream)
    try {
        $writer.Write([uint16]0)
        $writer.Write([uint16]1)
        $writer.Write([uint16]1)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([uint16]1)
        $writer.Write([uint16]32)
        $writer.Write([uint32]$png.Length)
        $writer.Write([uint32]22)
        $writer.Write($png)
    } finally {
        $writer.Dispose()
    }
} finally {
    $ring.Dispose()
    $white.Dispose()
    $dark.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
}
