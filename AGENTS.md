# Repository Guidelines

Do not execute any installation commands; continue modifying the code directly.
Regarding the garbled characters issue you mentioned, I've confirmed it's not a file corruption problem. In PowerShell, you can read it directly like this:

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$OutputEncoding = [System.Text.Encoding]::UTF8

Get-Content -Encoding UTF8 src\controllers\ttsController.ts
