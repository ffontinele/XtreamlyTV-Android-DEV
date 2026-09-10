# XtreamlyTV Android APK — X-Drayme Edition

Versao Android (TV Box + celular) do XtreamlyTV, empacotada automaticamente pelo GitHub Actions.

## Funcionalidades

- **Kotlin/Compose nativo** com AndroidX Media3 ExoPlayer
- **Suporte a HLS e MPEG-TS** (streams ao vivo)
- **Login Xtream Codes** (servidor, usuario, senha)
- **Catalogos Live TV, Movies e Series** com busca por categoria
- **Sistema de favoritos** com grupos customizaveis
- **Tema escuro** otimizado para TV
- **Navegacao por controle remoto** (Leanback Launcher)

## Download do APK

O APK e gerado automaticamente a cada push. Baixe em:

**[Releases](../../releases)** -> versao mais recente -> `app-debug.apk`

Ou em qualquer commit: **Actions** -> workflow mais recente -> aba **Artifacts** -> `X-Drayme-APK`

## Instalacao

```bash
adb install -r app-debug.apk
```

Ou abra o APK diretamente no gerenciador de arquivos (permita instalacao de fontes desconhecidas).

## Build local (opcional)

```bash
./gradlew :app:assembleDebug
# APK gerado em: app/build/outputs/apk/debug/app-debug.apk
```

## Requisitos

- Android 6.0+ (API 23)
- TV Box: qualquer com Leanback Launcher
- Celular: funciona em landscape

## Aviso

Este app nao inclui canais, filmes ou subscrioes. Use apenas com provedores Xtream que voce tem autorizacao de acesso.
