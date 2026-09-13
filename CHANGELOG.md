# 📜 Changelog — XtreamlyTV

Todas as melhorias implementadas e testadas em aparelho real durante o projeto.

## 🎬 v1.6.0 — Navegação de episódios no player
- Botões **◀ Ep ant.** e **Ep próx. ▶** na barra do player (séries e filmes com fila)
- **D-pad inteligente**: ↑/↓ troca episódio em séries; mantém zapping de canais ao vivo
- Bordas seguras: no primeiro/último episódio o botão não faz nada (sem crash)
- Botão voltar retorna à tela de origem sem empilhar players

## 📂 v1.5.x — Vídeos offline + menu rolável
- Nova tela **"Vídeos offline"** no menu lateral (abaixo de Settings)
- Seção **"Baixando agora"**: barra de progresso ao vivo com % e MB baixados / MB totais + botão Cancelar
- Seção **Falhas** com botão Remover
- **▶ Tocar**: reproduz o arquivo baixado no player do próprio app, **sem internet**
- **🗑 Excluir**: apaga o arquivo do aparelho e da lista
- Tela se atualiza sozinha (progresso ao vivo)
- **Menu lateral rolável**: todos os itens no tamanho padrão, sem espremer

## ⬇️ v1.4.x — Download de vídeos e Copiar link
- Botão **⬇ Download** em filmes e episódios → salva em `Documents/XtreamlyTV/Downloads/`
- Botão **🔗 Link**: copia a URL do stream para a área de transferência
- Botões grandes nos episódios (alvo de toque 44dp), separados do play
- Notificação de download com progresso na barra do Android

## 💾 v1.3.x — Backup & Restauração de contas
- Card **"Backup & Restauracao"** em Configurações
- **Exportar contas**: salva todas em `Documents/XtreamlyTV/backup.xtreamly` (JSON real)
- **Importar backup**: restaura contas e recarrega o provedor ativo automaticamente
- Ideal para trocar de aparelho ou reinstalar sem redigitar senhas

## ▶️ v1.1.x / v1.2.x — Base estável e player completo
- Botão **"Continue SxEy"** em séries: vai direto ao episódio onde você parou
- Filmes retomam de onde pararam (progresso salvo localmente)
- **Volume contínuo** no player + **double-tap ±30s**
- Gestos: arrasto vertical à direita = volume; à esquerda = brilho
- Posters full-bleed nos cards
- CI inteligente: **1 build por tag** (sem builds duplicadas)

## 🎨 Recursos da base (mantidos e validados)
- 5 temas: Teal, Graphite, Purple, Pink e Blue
- Favoritos com grupos personalizados e ordenação
- Busca, histórico e progresso locais — nada sai do seu aparelho
- Sem analytics, sem anúncios

---
Feito com rum e paciência, das 22h até o amanhecer. 🍹
