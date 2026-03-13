Add-Type -AssemblyName System.Drawing

$spriteDir = Join-Path $PSScriptRoot "..\app\src\main\res\drawable-nodpi"
$spriteDir = (Resolve-Path $spriteDir).Path
$yellow = [System.Drawing.Color]::FromArgb(255, 0xF2, 0xFF, 0x00)

Get-ChildItem $spriteDir -Filter "guide_*.png" | ForEach-Object {
    $path = $_.FullName
    $source = [System.Drawing.Bitmap]::FromFile($path)
    $bitmap = New-Object System.Drawing.Bitmap($source.Width, $source.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.DrawImage($source, 0, 0, $source.Width, $source.Height)
        } finally {
            $graphics.Dispose()
            $source.Dispose()
        }

        $bg = $bitmap.GetPixel(0, 0)
        for ($y = 0; $y -lt $bitmap.Height; $y++) {
            for ($x = 0; $x -lt $bitmap.Width; $x++) {
                $pixel = $bitmap.GetPixel($x, $y)
                $sameBg = (
                    $pixel.A -eq $bg.A -and
                    $pixel.R -eq $bg.R -and
                    $pixel.G -eq $bg.G -and
                    $pixel.B -eq $bg.B
                )
                if ($sameBg) {
                    $bitmap.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                } elseif ($pixel.A -gt 0) {
                    $bitmap.SetPixel($x, $y, $yellow)
                }
            }
        }
        $tmp = "$path.tmp"
        $bitmap.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
    Move-Item $tmp $path -Force
}
