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

## 📜 Changelog — melhorias do projeto

- **v1.6.0** — Botões ◀ Ep ant. / Ep próx. ▶ no player + D-pad inteligente (↑/↓ troca episódio em séries, zapping em ao vivo)
- **v1.5.x** — Tela "Vídeos offline" (progresso ao vivo % + MB, Tocar sem internet, Excluir) + menu lateral rolável
- **v1.4.x** — Download de vídeos (`Documents/XtreamlyTV/Downloads/`) + botão Copiar link + botões 44dp nos episódios
- **v1.3.x** — Backup & Restauração de contas (`Documents/XtreamlyTV/backup.xtreamly`)
- **v1.1.x/v1.2.x** — Continue SxEy, resume de filmes, volume contínuo, double-tap ±30s, gestos, CI 1 build por tag
- **Base** — 5 temas, favoritos com grupos, busca/histórico locais, sem analytics e sem anúncios

📄 Changelog completo e detalhado: [CHANGELOG.md](CHANGELOG.md)
