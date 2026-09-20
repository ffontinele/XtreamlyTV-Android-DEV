# 🏁 XtreamlyTV Android — v1.6.3

## ✨ Novidades

- **Botão "Validade" no card de provedores**: consulta o provedor em tempo real e mostra:
  - "Expira em dd/mm/aaaa · faltam X dias" (verde)
  - "Expira em dd/mm/aaaa · há X dias (EXPIRADA)" (vermelho)
  - "Conta ativa · sem data de expiração" (para listas ilimitadas)
- **QR Code gigante e nítido**: o QR agora ocupa quase todo o diálogo de adicionar provedor, com módulos desenhados pixel-perfect e ECC alto (ErrorCorrectionLevel.H) — leitura muito mais confiável pelo celular.
- **Mensagem do QR em português**: "Adicionar provedor via QR Code · Escaneie com o celular para enviar uma lista pra TV".

## Já incluído nas versões anteriores
- **v1.6.2:** QR migrado para `zui-sync` + versionName alinhado.
- **v1.6.1:** conta expirada/404 cai na Home com aviso (nunca mais trava no login).
- **v1.6.0:** botões ◀ Ep ant. / Ep próx. ▶ no player + D-pad inteligente.

# 🏁 XtreamlyTV Android — v1.6.2

## O que mudou nesta versão
- **QR Code Cloud Sync agora aponta para o painel dedicado** `ffontinele.github.io/zui-sync/` (mesma base usada pelo app webOS v0.7.1+).
- Painel antigo (`ZUI_IPTV_Player_portugues/painel_web`) foi substituído; ao escanear o QR do app, abre direto o novo painel web.
- **Limpeza de versão interna**: `versionName` atualizado de 0.8.0 para 1.6.2 (alinhado com as releases do GitHub).
- Mantido: abertura direta na Home na primeira instalação; falha de conexão cai na Home com aviso; ◀ Ep ant. / Ep próx. ▶ no player.

# 🏁 XtreamlyTV — v1.6.1

## O que mudou nesta versão
- Conta expirada ou com erro (HTTP 404) não joga mais o usuário na tela de login: o app vai direto para a Home, com aviso discreto de conexão.
- A tela de login nunca aparece sozinha: só abre pelo caminho manual de adicionar/trocar conta em Configurações.
- Mantido: abertura direta na Home na primeira instalação, mesmo sem contas salvas.

## Já incluído nas versões anteriores
- **v1.6.0:** botões ◀ Ep ant. / Ep próx. ▶ no player + D-pad inteligente (↑/↓ troca episódio em séries; zapping em canais ao vivo).
- **v1.5.x:** tela "Vídeos offline" (progresso ao vivo % + MB, Tocar sem internet, Excluir) + menu lateral rolável.
- **v1.4.x:** Download de vídeos (Documents/XtreamlyTV/Downloads/) + botão Copiar link.
- **v1.3.x:** Backup & Restauração de contas (Documents/XtreamlyTV/backup.xtreamly).

---
*Este arquivo é atualizado a cada nova versão e vai automaticamente no corpo da Release, junto com o APK.*
