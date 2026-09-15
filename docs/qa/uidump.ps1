# UI dump helper for QA testing (MyMediaPlayer)
# Usage: . .\uidump.ps1  → dumps current UI hierarchy and prints text+bounds lines
param()
$ErrorActionPreference = "Stop"
adb shell rm -f /sdcard/ui.xml 2>$null | Out-Null
$out = adb shell uiautomator dump /sdcard/ui.xml 2>$null
if ($LASTEXITCODE -ne 0) { Write-Host "DUMP FAILED: $out"; exit 1 }
adb pull /sdcard/ui.xml "$env:TEMP\ui.xml" 2>$null | Out-Null
$ui = [System.IO.File]::ReadAllText("$env:TEMP\ui.xml", [System.Text.Encoding]::UTF8)
$nodes = [regex]::Matches($ui, '<node[^>]*text="([^"]*)"[^>]*content-desc="([^"]*)"[^>]*class="([^"]*)"[^>]*bounds="(\[[^\]]+\]\[[^\]]+\])"')
$outLines = @()
foreach ($n in $nodes) {
    $text = $n.Groups[1].Value
    $desc = $n.Groups[2].Value
    $cls = $n.Groups[3].Value
    $bounds = $n.Groups[4].Value
    $label = $text
    if ($desc) { $label = "$text [desc:$desc]" }
    if ($label) {
        $short = $cls -replace 'android.(widget|view|views)\.',''
        $outLines += ("{0,-28} {1}" -f $short, "$label  $bounds")
    }
}
$outLines | Sort-Object -Unique