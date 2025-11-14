
# QR Post Scanner (Android)

Aplicação Android simples para **ler QR Codes** de cartas postais, **guardar** o número lido com **data/hora** e **localização GPS**, e **exportar** as leituras para um ficheiro **CSV** via seletor do sistema (Storage Access Framework).

## Funcionalidades
- Leitura de QR Codes com **CameraX** + **ML Kit**.
- Captura de **data/hora (ISO 8601 UTC)** e **GPS (lat, lon)** por leitura.
- Lista de leituras no ecrã.
- **Exportação para CSV** usando `ACTION_CREATE_DOCUMENT` (o utilizador escolhe onde guardar).

## Requisitos
- Android Studio (Giraffe/Koala+).
- Dispositivo Android com Google Play Services para localização.

## Como compilar
1. Abrir o projeto no Android Studio (`File > Open...`).
2. Conectar um dispositivo (ou emulador com câmera simulada).
3. `Run > Run 'app'`.

## Permissões
- **Câmara** (obrigatória para ler o QR).
- **Localização precisa** (para obter lat/lon no momento da leitura).

## Exportação CSV
- Use o botão **Exportar CSV**. Será aberto o seletor do sistema para escolher o nome/local. O ficheiro tem cabeçalho: `qr_code,timestamp_utc,latitude,longitude`.

## Observações
- A app guarda as leituras **em memória** enquanto está aberta. Use **Exportar CSV** para persistir fora da app.
