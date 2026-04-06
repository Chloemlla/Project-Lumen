param(
  [Parameter(Mandatory = $true)]
  [string]$Command
)

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8NoBom
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom
$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'
$PSDefaultParameterValues['Set-Content:Encoding'] = 'utf8'
$PSDefaultParameterValues['Add-Content:Encoding'] = 'utf8'
chcp 65001 > $null

$messages = New-Object System.Collections.Generic.List[string]
$exitCode = 0
$success = $true

try {
  $result = Invoke-Expression $Command 2>&1
  foreach ($item in $result) {
    $messages.Add([string]$item)
  }
  if ($LASTEXITCODE) {
    $exitCode = $LASTEXITCODE
    $success = $false
  }
}
catch {
  $exitCode = 1
  $success = $false
  $messages.Add($_.Exception.Message)
}

[pscustomobject]@{
  command = $Command
  encoding = 'utf-8'
  success = $success
  exit_code = $exitCode
  output = $messages
} | ConvertTo-Json -Depth 4
