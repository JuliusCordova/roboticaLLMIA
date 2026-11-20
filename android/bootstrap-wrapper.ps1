# Descarga el Gradle Wrapper oficial antes de abrir el proyecto en Android Studio.
# Requiere Internet; no instala software global.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$destination = Join-Path $root 'gradle\wrapper\gradle-wrapper.jar'
if (Test-Path $destination) { Write-Host 'Gradle wrapper ya disponible.'; exit 0 }
$uri = 'https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar'
Write-Host 'Descargando Gradle Wrapper desde el repositorio oficial de Gradle...'
Invoke-WebRequest -Uri $uri -OutFile $destination -MaximumRedirection 3
if ((Get-Item $destination).Length -lt 30000) {
    Remove-Item $destination -ErrorAction SilentlyContinue
    throw 'No se descargó un JAR de Gradle válido. Verifique la URL y la conexión.'
}
Write-Host 'Listo. Ahora abre esta carpeta en Android Studio.'
